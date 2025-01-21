package warlockfe.warlock3.core.script

import java.io.File

interface WarlockScriptEngineRepository {

    suspend fun getScript(name: String, characterId: String, scriptManager: ScriptManager): ScriptInstance?

    suspend fun getScript(file: File, scriptManager: ScriptManager): ScriptInstance?

    fun supportsExtension(extension: String): Boolean
}