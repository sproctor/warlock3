package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle

/**
 * The scripts running against one game: pausing, resuming and stopping them, one by one or all
 * together, and listing them, in the scripts window and in answer to the client's `/` commands.
 * [ScriptLauncher] starts them.
 */
internal class ScriptControl(
    private val client: WarlockClient,
    private val scriptManager: ScriptManager,
    scope: CoroutineScope,
) {
    init {
        listRunningScripts(scope)
    }

    /** The names of the scripts running now. */
    fun runningNames(): List<String> =
        scriptManager.runningScripts.value.values
            .map { it.instance.name }

    /** Stops each script in [names], by name or id, telling the user what became of each. */
    suspend fun stop(names: List<String>) {
        forEachNamed(names) { name, script ->
            script.stop()
            client.print(StyledString("Script $name stopped.", WarlockStyle.Echo))
        }
    }

    /** Pauses each script in [names], by name or id. */
    suspend fun pause(names: List<String>) {
        forEachNamed(names) { name, script ->
            script.suspend()
            client.print(StyledString("Script $name paused.", WarlockStyle.Echo))
        }
    }

    /** Resumes each script in [names], by name or id. */
    suspend fun resume(names: List<String>) {
        forEachNamed(names) { name, script ->
            script.resume()
            client.print(StyledString("Script $name resumed.", WarlockStyle.Echo))
        }
    }

    /** Prints the running scripts, each with the id the other commands accept. */
    suspend fun printRunning() {
        val scripts = scriptManager.runningScripts.value
        if (scripts.isEmpty()) {
            client.print(StyledString("No scripts are running", WarlockStyle.Echo))
        } else {
            client.print(StyledString("Running scripts:", WarlockStyle.Echo))
            scripts.forEach {
                client.print(StyledString("${it.value.instance.name} - ${it.key}", WarlockStyle.Echo))
            }
        }
    }

    private suspend fun forEachNamed(
        names: List<String>,
        action: suspend (name: String, script: ScriptInstance) -> Unit,
    ) {
        names.forEach { name ->
            val script = scriptManager.findScriptInstance(name)
            if (script != null) {
                action(name, script)
            } else {
                client.print(StyledString("Script $name not found.", WarlockStyle.Error))
            }
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
    private fun listRunningScripts(scope: CoroutineScope) {
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
}
