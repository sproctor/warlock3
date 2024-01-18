package warlockfe.warlock3.core.script.wsl

import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.WarlockScriptEngine
import warlockfe.warlock3.core.script.WarlockScriptEngineRegistry
import java.io.File

class WslEngine(
    private val highlightRepository: HighlightRepository,
    private val variableRepository: VariableRepository,
    private val scriptEngineRegistry: WarlockScriptEngineRegistry,
) : WarlockScriptEngine {
    override val extensions: List<String> = listOf("wsl", "cmd", "wiz")

    override fun createInstance(name: String, file: File): ScriptInstance {
        val script = WslScript(name, file)
        return WslScriptInstance(
            name = name,
            script = script,
            highlightRepository = highlightRepository,
            variableRepository = variableRepository,
            scriptEngineRegistry = scriptEngineRegistry,
        )
    }
}