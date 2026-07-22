package warlockfe.warlock3.scripting.lua

import com.seanproctor.lua.LuaConfig
import com.seanproctor.lua.LuaState
import com.seanproctor.lua.LuaSyntaxError
import com.seanproctor.lua.LuaValue
import com.seanproctor.lua.StdLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readString
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.util.parseArguments

/** Thrown by the script bindings to unwind a script that has been stopped. */
class StopException : Exception("Script stopped")

class LuaScriptInstance(
    override val id: Long,
    override val name: String,
    private val file: Path,
    private val variableRepository: VariableRepository,
    private val scriptManager: ScriptManager,
    private val fileSystem: FileSystem,
) : ScriptInstance {
    internal val statusFlow = MutableStateFlow(ScriptStatus.NotStarted)

    override val status: ScriptStatus
        get() = statusFlow.value

    internal val scope = CoroutineScope(Dispatchers.IO)

    override fun start(
        client: WarlockClient,
        argumentString: String,
        onStop: () -> Unit,
        commandHandler: suspend (String) -> SendCommandType,
    ) {
        setStatus(ScriptStatus.Running)
        val arguments = parseArguments(argumentString)
        scope.launch {
            try {
                val code = fileSystem.source(file).buffered().use { it.readString() }
                // The whole interpreter lifetime stays inside this one blocking block, so the
                // state never leaves the thread it was created on (LuaState is thread-confined).
                // DEBUG is opened only so the bindings can install their watchdog hook; the
                // bootstrap removes the debug table before the script runs.
                LuaState(LuaConfig(stdlibs = StdLib.SAFE_DEFAULT + StdLib.DEBUG)).use { lua ->
                    LuaBindings(lua, client, this@LuaScriptInstance, variableRepository).install()
                    val chunk = lua.load(code, "@$name")
                    lua.call(chunk, arguments.map { LuaValue.Str(it) })
                }
            } catch (e: LuaSyntaxError) {
                client.print(StyledString("Script error: ${e.message}", style = WarlockStyle.Error))
            } catch (e: Exception) {
                // A stop() or exit() unwinds the script with a Lua error whose original exception
                // is lost at the interpreter boundary, so a stopped status means a clean shutdown.
                if (statusFlow.value != ScriptStatus.Stopped) {
                    client.print(StyledString("Script error: ${e.message}", style = WarlockStyle.Error))
                }
            } finally {
                setStatus(ScriptStatus.Stopped)
                onStop()
                scope.cancel()
            }
        }
    }

    override fun stop() {
        setStatus(ScriptStatus.Stopped)
        // Aborts any wait the script is blocked in; the script thread then unwinds on its own.
        scope.cancel()
    }

    override fun suspend() {
        setStatus(ScriptStatus.Suspended)
    }

    override fun resume() {
        setStatus(ScriptStatus.Running)
    }

    internal fun setStatus(newStatus: ScriptStatus) {
        statusFlow.value = newStatus
        scriptManager.scriptStateChanged(this)
    }

    /**
     * Called from the bindings on the script's thread: blocks while the script is suspended and
     * throws [StopException] once it has been stopped.
     */
    internal fun checkStatus() {
        if (statusFlow.value == ScriptStatus.Suspended) {
            runBlocking { statusFlow.first { it != ScriptStatus.Suspended } }
        }
        if (statusFlow.value == ScriptStatus.Stopped) {
            throw StopException()
        }
    }

    /** Suspending equivalent of [checkStatus] for use inside the bindings' wait loops. */
    internal suspend fun awaitRunning() {
        statusFlow.first { it != ScriptStatus.Suspended }
        if (statusFlow.value == ScriptStatus.Stopped) {
            throw StopException()
        }
    }
}
