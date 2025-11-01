package warlockfe.warlock3.core.script

import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.files.Path
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient

interface ScriptManager {

    val runningScripts: StateFlow<Map<Long, ScriptData>>

    suspend fun startScript(client: WarlockClient, command: String, commandHandler: suspend (String) -> SendCommandType)

    suspend fun startScript(client: WarlockClient, file: Path, commandHandler: suspend (String) -> SendCommandType)

    fun findScriptInstance(description: String): ScriptInstance?

    fun scriptStateChanged(instance: ScriptInstance)
}

data class ScriptData(val status: ScriptStatus, val instance: ScriptInstance)