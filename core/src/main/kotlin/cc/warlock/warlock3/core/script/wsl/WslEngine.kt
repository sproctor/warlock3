package cc.warlock.warlock3.core.script.wsl

import cc.warlock.warlock3.core.highlights.HighlightRegistry
import cc.warlock.warlock3.core.script.WarlockScriptEngine
import cc.warlock.warlock3.core.script.ScriptInstance
import cc.warlock.warlock3.core.script.VariableRegistry
import java.io.File

class WslEngine(
    private val highlightRegistry: HighlightRegistry,
    private val variableRegistry: VariableRegistry,
) : WarlockScriptEngine {
    override val extensions: List<String> = listOf("wsl", "cmd", "wiz")

    override fun createInstance(name: String, file: File): ScriptInstance {
        val script = WslScript(name, file)
        return WslScriptInstance(name, script, highlightRegistry = highlightRegistry, variableRegistry = variableRegistry)
    }
}