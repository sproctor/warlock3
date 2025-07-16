package warlockfe.warlock3.scripting.wsl

import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.scripting.WarlockScriptEngine
import java.io.File

class WslEngine(
    private val highlightRepository: HighlightRepository,
    private val variableRepository: VariableRepository,
    private val soundPlayer: SoundPlayer,
) : WarlockScriptEngine {
    override val extensions: List<String> = listOf("wsl", "cmd", "wiz")

    override fun createInstance(id: Long, name: String, file: File, scriptManager: ScriptManager): ScriptInstance {
        return WslScriptInstance(
            id = id,
            name = name,
            file = file,
            highlightRepository = highlightRepository,
            variableRepository = variableRepository,
            scriptManager = scriptManager,
            soundPlayer = soundPlayer,
        )
    }
}