package cc.warlock.warlock3.core.script.js

import cc.warlock.warlock3.core.script.ScriptInstance
import cc.warlock.warlock3.core.script.WarlockScriptEngine
import java.io.File
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

class JsEngine(
    private val manager: ScriptEngineManager
) : WarlockScriptEngine {
    override val extensions: List<String> = listOf("js")

    private val engine = manager.getEngineByName("rhino")

    override fun createInstance(name: String, file: File): ScriptInstance {
        return JsInstance(name, file, engine)
    }
}