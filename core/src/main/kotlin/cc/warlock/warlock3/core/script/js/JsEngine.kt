package cc.warlock.warlock3.core.script.js

import cc.warlock.warlock3.core.script.ScriptInstance
import cc.warlock.warlock3.core.script.VariableRegistry
import cc.warlock.warlock3.core.script.WarlockScriptEngine
import org.mozilla.javascript.ContextFactory
import java.io.File

class JsEngine(
    private val variableRegistry: VariableRegistry,
) : WarlockScriptEngine {

    override val extensions: List<String> = listOf("js")

    init {
        ContextFactory.initGlobal(InterruptableContextFactory())
    }

    override fun createInstance(name: String, file: File): ScriptInstance {
        return JsInstance(name, file, variableRegistry)
    }
}