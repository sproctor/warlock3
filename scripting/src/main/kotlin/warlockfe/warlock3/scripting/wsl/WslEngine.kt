package warlockfe.warlock3.scripting.wsl

import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.scripting.WarlockScriptEngine
import warlockfe.warlock3.scripting.WarlockScriptEngineRepositoryImpl
import java.io.File

class WslEngine(
    private val highlightRepository: HighlightRepository,
    private val variableRepository: VariableRepository,
) : WarlockScriptEngine {
    override val extensions: List<String> = listOf("wsl", "cmd", "wiz")

    override fun createInstance(name: String, file: File, scriptManager: ScriptManager): ScriptInstance {
        val script = WslScript(name, file)
        return WslScriptInstance(
            name = name,
            script = script,
            highlightRepository = highlightRepository,
            variableRepository = variableRepository,
            scriptManager = scriptManager,
        )
    }
}