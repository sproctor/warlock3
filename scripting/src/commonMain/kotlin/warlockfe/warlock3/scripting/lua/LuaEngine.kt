package warlockfe.warlock3.scripting.lua

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.scripting.WarlockScriptEngine

class LuaEngine(
    private val variableRepository: VariableRepository,
    private val fileSystem: FileSystem,
) : WarlockScriptEngine {
    override val extensions: List<String> = listOf("lua")

    override fun createInstance(
        id: Long,
        name: String,
        file: Path,
        scriptManager: ScriptManager,
    ): ScriptInstance = LuaScriptInstance(id, name, file, variableRepository, scriptManager, fileSystem)
}
