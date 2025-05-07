package warlockfe.warlock3.scripting.js

import org.mozilla.javascript.ContextFactory
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.scripting.WarlockScriptEngine
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class JsEngine(
    private val variableRepository: VariableRepository,
) : WarlockScriptEngine {

    override val extensions: List<String> = listOf("js")

    private val runningScripts = CopyOnWriteArrayList<JsInstance>()

    init {
        ContextFactory.initGlobal(InterruptibleContextFactory(runningScripts))
    }

    // FIXME: We leak instances here. Maybe we should clean them up?
    override fun createInstance(id: Long, name: String, file: File, scriptManager: ScriptManager): ScriptInstance {
        return JsInstance(id, name, file, variableRepository, scriptManager)
            .also { runningScripts.add(it) }
    }
}