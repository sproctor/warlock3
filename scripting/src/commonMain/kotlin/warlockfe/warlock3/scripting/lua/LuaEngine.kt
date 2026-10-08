package warlockfe.warlock3.scripting.lua

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.scripting.WarlockScriptEngine

/**
 * @param scriptDirs the script folders for a character, which `require` loads libraries from.
 * @param storageDir where `storage` keeps scripts' data, a folder per character.
 */
class LuaEngine(
    private val variableRepository: VariableRepository,
    private val fileSystem: FileSystem,
    private val scriptDirs: suspend (characterId: String) -> List<Path> = { emptyList() },
    private val storageDir: Path? = null,
) : WarlockScriptEngine {
    override val extensions: List<String> = listOf("lua")

    override fun createInstance(
        id: Long,
        name: String,
        file: Path,
        scriptManager: ScriptManager,
    ): ScriptInstance = LuaScriptInstance(id, name, file, null, variableRepository, scriptManager, fileSystem, scriptDirs, storageDir)

    override fun createStringInstance(
        id: Long,
        name: String,
        content: String,
        scriptManager: ScriptManager,
    ): ScriptInstance = LuaScriptInstance(id, name, null, content, variableRepository, scriptManager, fileSystem, scriptDirs, storageDir)
}
