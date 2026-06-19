package warlockfe.warlock3.scripting.wsl

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import warlockfe.warlock3.core.prefs.repositories.HighlightRepository
import warlockfe.warlock3.core.prefs.repositories.NameRepository
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.scripting.WarlockScriptEngine

class WslEngine(
    private val highlightRepository: HighlightRepository,
    private val nameRepository: NameRepository,
    private val variableRepository: VariableRepository,
    private val soundPlayer: SoundPlayer,
    private val fileSystem: FileSystem,
) : WarlockScriptEngine {
    override val extensions: List<String> = listOf("wsl", "cmd", "wiz")

    override fun createInstance(
        id: Long,
        name: String,
        file: Path,
        scriptManager: ScriptManager,
    ): ScriptInstance =
        WslScriptInstance(
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

    /** Build an instance that runs an in-memory WSL [content] string (e.g. an action button script). */
    fun createStringInstance(
        id: Long,
        name: String,
        content: String,
        scriptManager: ScriptManager,
    ): ScriptInstance =
        WslScriptInstance(
            id = id,
            name = name,
            file = null,
            content = content,
            highlightRepository = highlightRepository,
            nameRepository = nameRepository,
            variableRepository = variableRepository,
            scriptManager = scriptManager,
            soundPlayer = soundPlayer,
            fileSystem = fileSystem,
        )
}
