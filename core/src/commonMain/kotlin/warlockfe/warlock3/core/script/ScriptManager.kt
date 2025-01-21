package warlockfe.warlock3.core.script

import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.core.client.WarlockClient
import java.io.File

interface ScriptManager {

    val runningScripts: StateFlow<Map<Long, ScriptInstance>>

    suspend fun startScript(client: WarlockClient, command: String)

    suspend fun startScript(client: WarlockClient, file: File)

    fun findScriptInstance(description: String): ScriptInstance?

    fun scriptStateChanged(instance: ScriptInstance)
}
