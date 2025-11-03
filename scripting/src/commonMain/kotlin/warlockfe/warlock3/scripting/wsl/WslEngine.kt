package warlockfe.warlock3.scripting.wsl

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.NameRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.scripting.WarlockScriptEngine

class WslEngine(
    private val highlightRepository: HighlightRepositoryImpl,
    private val nameRepository: NameRepositoryImpl,
    private val variableRepository: VariableRepository,
    private val soundPlayer: SoundPlayer,
    private val fileSystem: FileSystem,
) : WarlockScriptEngine {
    override val extensions: List<String> = listOf("wsl", "cmd", "wiz")

    override fun createInstance(id: Long, name: String, file: Path, scriptManager: ScriptManager): ScriptInstance {
        return WslScriptInstance(
            id = id,
            name = name,
            file = file,
            highlightRepository = highlightRepository,
            nameRepository = nameRepository,
            variableRepository = variableRepository,
            scriptManager = scriptManager,
            soundPlayer = soundPlayer,
            fileSystem = fileSystem,
        )
    }
}