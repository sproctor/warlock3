package warlockfe.warlock3.core.script

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient
import java.io.File

interface ScriptManager {

    val runningScripts: StateFlow<Map<Long, ScriptData>>

    suspend fun startScript(client: WarlockClient, command: String, commandHandler: suspend (String) -> SendCommandType)

    suspend fun startScript(client: WarlockClient, file: File, commandHandler: suspend (String) -> SendCommandType)

    fun findScriptInstance(description: String): ScriptInstance?

    fun scriptStateChanged(instance: ScriptInstance)
}

data class ScriptData(val status: ScriptStatus, val instance: ScriptInstance)