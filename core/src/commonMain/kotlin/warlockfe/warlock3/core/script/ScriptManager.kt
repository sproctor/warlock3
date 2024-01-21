package warlockfe.warlock3.core.script

import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.core.client.WarlockClient
import java.io.File

interface ScriptManager {

    val scriptInfo: StateFlow<Map<Long, ScriptInfo>>

    val runningScripts: Set<ScriptInstance>

    suspend fun startScript(client: WarlockClient, command: String)

    suspend fun startScript(client: WarlockClient, file: File)

    fun findScriptInstance(description: String): ScriptInstance?

    fun supportsExtension(extension: String): Boolean
}
