package warlockfe.warlock3.core.script.js

import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.WarlockScriptEngine
import warlockfe.warlock3.core.script.WarlockScriptEngineRegistry
import org.mozilla.javascript.ContextFactory
import java.io.File

class JsEngine(
    private val variableRepository: VariableRepository,
    private val scriptEngineRegistry: WarlockScriptEngineRegistry
) : WarlockScriptEngine {

    override val extensions: List<String> = listOf("js")

    init {
        ContextFactory.initGlobal(InterruptableContextFactory(scriptEngineRegistry))
    }

    override fun createInstance(name: String, file: File): ScriptInstance {
        return JsInstance(name, file, variableRepository, scriptEngineRegistry)
    }
}