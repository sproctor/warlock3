package warlockfe.warlock3.core.script

import kotlinx.io.files.Path

interface WarlockScriptEngineRepository {

    suspend fun getScript(name: String, characterId: String, scriptManager: ScriptManager): ScriptLaunchResult

    suspend fun getScript(file: Path, scriptManager: ScriptManager): ScriptLaunchResult

    fun supportsExtension(extension: String): Boolean
}

sealed interface ScriptLaunchResult {
    data class Success(val instance: ScriptInstance) : ScriptLaunchResult
    data class Failure(val message: String) : ScriptLaunchResult
}
