package warlockfe.warlock3.core.script

import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.files.Path
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient

interface ScriptManager {
    val runningScripts: StateFlow<Map<Long, ScriptData>>

    suspend fun startScript(
        client: WarlockClient,
        command: String,
        commandHandler: suspend (String) -> SendCommandType,
    )

    suspend fun startScript(
        client: WarlockClient,
        file: Path,
        commandHandler: suspend (String) -> SendCommandType,
    )

    /**
     * Run a script from a raw string (an action button's inline script, or the one a MUD sent),
     * in the language the file [extension] names: `wsl` by default, or `lua`.
     */
    suspend fun startScript(
        client: WarlockClient,
        name: String,
        contents: String,
        commandHandler: suspend (String) -> SendCommandType,
        extension: String = "wsl",
    )

    fun findScriptInstance(description: String): ScriptInstance?

    fun scriptStateChanged(instance: ScriptInstance)
}

data class ScriptData(
    val status: ScriptStatus,
    val instance: ScriptInstance,
)
