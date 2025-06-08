package warlockfe.warlock3.core.script

import java.io.File

interface WarlockScriptEngineRepository {

    suspend fun getScript(name: String, characterId: String, scriptManager: ScriptManager): ScriptLaunchResult

    suspend fun getScript(file: File, scriptManager: ScriptManager): ScriptLaunchResult

    fun supportsExtension(extension: String): Boolean
}

sealed interface ScriptLaunchResult {
    data class Success(val instance: ScriptInstance) : ScriptLaunchResult
    data class Failure(val message: String) : ScriptLaunchResult
}
