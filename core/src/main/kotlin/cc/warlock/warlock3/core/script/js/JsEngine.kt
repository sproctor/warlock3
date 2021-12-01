package cc.warlock.warlock3.core.script.js

import cc.warlock.warlock3.core.script.ScriptInstance
import cc.warlock.warlock3.core.script.VariableRegistry
import cc.warlock.warlock3.core.script.WarlockScriptEngine
import cc.warlock.warlock3.core.script.WarlockScriptEngineRegistry
import org.mozilla.javascript.ContextFactory
import java.io.File

class JsEngine(
    private val variableRegistry: VariableRegistry,
    scriptEngineRegistry: WarlockScriptEngineRegistry
) : WarlockScriptEngine {

    override val extensions: List<String> = listOf("js")

    init {
        ContextFactory.initGlobal(InterruptableContextFactory(scriptEngineRegistry))
    }

    override fun createInstance(name: String, file: File): ScriptInstance {
        return JsInstance(name, file, variableRegistry)
    }
}