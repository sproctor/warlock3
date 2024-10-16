package warlockfe.warlock3.scripting.js

import org.mozilla.javascript.ContextFactory
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.scripting.WarlockScriptEngine
import warlockfe.warlock3.scripting.WarlockScriptEngineRegistry
import java.io.File

class JsEngine(
    private val variableRepository: VariableRepository,
    private val scriptEngineRegistry: WarlockScriptEngineRegistry
) : WarlockScriptEngine {

    override val extensions: List<String> = listOf("js")

    init {
        ContextFactory.initGlobal(InterruptibleContextFactory(scriptEngineRegistry))
    }

    override fun createInstance(name: String, id: Long, file: File): ScriptInstance {
        return JsInstance(name, id, file, variableRepository, scriptEngineRegistry)
    }
}