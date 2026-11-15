package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import warlockfe.warlock3.core.client.ScriptableClient
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.models.Action
import warlockfe.warlock3.core.script.MudScriptLoad
import warlockfe.warlock3.core.script.MudScriptStore
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle

/** The name the MUD's script runs under, so a new version replaces the last and `/kill` can find it. */
const val MUD_SCRIPT_NAME = "mud-script"

/**
 * The scripts running against one game: starting them, pausing and stopping them all, and listing
 * them in the scripts window. Every script sends its commands through [commands].
 */
internal class GameScripts(
    private val client: WarlockClient,
    private val scriptManager: ScriptManager,
    private val mudScriptStore: MudScriptStore,
    private val commands: CommandHandler,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    init {
        listRunningScripts()
        runTheMudsScript()
    }

    /** The names of the scripts running now. */
    fun runningNames(): List<String> =
        scriptManager.runningScripts.value.values
            .map { it.instance.name }

    fun runFile(file: Path) {
        scope.launch(ioDispatcher) {
            scriptManager.startScript(client, file, commands::handle)
        }
    }

    /** Run a leaf action button's inline WSL script. No-op for a group (which has no script). */
    fun runAction(action: Action) {
        val script = action.script ?: return
        scope.launch(ioDispatcher) {
            scriptManager.startScript(client, action.name, script, commands::handle)
        }
    }

    /** Stops every running script, telling the user how many. */
    suspend fun stopAll() {
        val scripts = scriptManager.runningScripts.value.values
        val count = scripts.size
        if (count > 0) {
            scripts.forEach { script ->
                script.instance.stop()
            }
            client.print(StyledString("Stopped $count script(s)"))
        }
    }

    /** Stops every running script without a word, because the game is going away. */
    fun stopForTeardown() {
        scriptManager.runningScripts.value.values
            .forEach { it.instance.stop() }
    }

    /** Pauses every running script, or resumes them all when none is running. */
    suspend fun togglePauseAll() {
        val scriptInstances =
            scriptManager.runningScripts.value.values
                .map { it.instance }
        if (scriptInstances.isNotEmpty()) {
            val paused = !scriptInstances.any { it.status == ScriptStatus.Running }
            if (paused) {
                client.print(StyledString("Resumed script(s)"))
            } else {
                client.print(StyledString("Paused script(s)"))
            }
            for (instance in scriptInstances) {
                if (paused) {
                    instance.resume()
                } else {
                    instance.suspend()
                }
            }
        } else {
            client.print(StyledString("No scripts running"))
        }
    }

    /** Tell the user which scripts the reconnect that opened this game stopped on the old one. */
    suspend fun reportStoppedByReconnect(names: List<String>) {
        client.print(
            StyledString("Reconnecting stopped ${names.size} script(s): ${names.joinToString()}", WarlockStyle.Echo),
        )
    }

    // Render the running-scripts status lines (with pause/resume/stop links) into the scripts window.
    private fun listRunningScripts() {
        scriptManager.runningScripts
            .onEach { scripts ->
                val scriptStream = client.getStream("warlockscripts")
                scriptStream.clear()
                scripts.forEach { entry ->
                    val instance = entry.value.instance
                    var text = StyledString("${instance.name}: ${instance.status} ")
                    when (instance.status) {
                        ScriptStatus.Running -> {
                            text +=
                                StyledString(
                                    "pause",
                                    WarlockStyle.Link(WarlockAction.SendCommand("/pause ${entry.key}")),
                                )
                        }

                        ScriptStatus.Suspended -> {
                            text +=
                                StyledString(
                                    "resume",
                                    WarlockStyle.Link(WarlockAction.SendCommand("/resume ${entry.key}")),
                                )
                        }

                        else -> {
                            // do nothing
                        }
                    }
                    text += StyledString(" ") +
                        StyledString("stop", WarlockStyle.Link(WarlockAction.SendCommand("/kill ${entry.key}")))
                    scriptStream.appendLine(text, false)
                }
            }.launchIn(scope)
    }

    /**
     * Runs the script a MUD offers over GMCP, when the connection is one that can be offered one
     * (see [ScriptableClient]) and the user has not declined. The offer is a state, not an event,
     * so one made while the connection was still being set up is not missed; a new version
     * offered mid-session replaces the running script, which goes by a fixed name for that.
     */
    private fun runTheMudsScript() {
        val scriptable = client as? ScriptableClient ?: return
        scope.launch(ioDispatcher) {
            scriptable.mudScript.filterNotNull().collect { offer ->
                val characterId = client.characterId.value ?: return@collect
                when (val load = mudScriptStore.load(characterId, offer)) {
                    is MudScriptLoad.Loaded -> {
                        client.print(StyledString("The MUD sent a script (version ${offer.version}).", WarlockStyle.Echo))
                        scriptManager.startScript(client, MUD_SCRIPT_NAME, load.script, commands::handle, extension = "lua")
                    }

                    is MudScriptLoad.Failed -> {
                        client.print(StyledString(load.message, WarlockStyle.Error))
                    }

                    is MudScriptLoad.Skipped -> {
                        client.debug(load.reason)
                    }
                }
            }
        }
    }
}
