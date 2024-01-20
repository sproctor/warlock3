package warlockfe.warlock3.scripting.wsl

import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.scripting.WarlockScriptEngine
import warlockfe.warlock3.scripting.WarlockScriptEngineRegistry
import java.io.File

class WslEngine(
    private val highlightRepository: HighlightRepository,
    private val variableRepository: VariableRepository,
    private val scriptEngineRegistry: WarlockScriptEngineRegistry,
) : WarlockScriptEngine {
    override val extensions: List<String> = listOf("wsl", "cmd", "wiz")

    override fun createInstance(name: String, id: Long, file: File): ScriptInstance {
        val script = WslScript(name, file)
        return WslScriptInstance(
            name = name,
            id = id,
            script = script,
            highlightRepository = highlightRepository,
            variableRepository = variableRepository,
            scriptEngineRegistry = scriptEngineRegistry,
        )
    }
}