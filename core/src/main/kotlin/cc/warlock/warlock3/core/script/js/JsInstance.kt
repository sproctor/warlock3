package cc.warlock.warlock3.core.script.js

import cc.warlock.warlock3.core.client.WarlockClient
import cc.warlock.warlock3.core.script.ScriptInstance
import cc.warlock.warlock3.core.util.parseArguments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

class JsInstance(
    override val name: String,
    private val file: File,
    private val engine: ScriptEngine,
) : ScriptInstance {

    private var _isRunning = false
    override val isRunning: Boolean
        get() = _isRunning

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun start(client: WarlockClient, argumentString: String) {
        _isRunning = true
        scope.launch {
            val reader = InputStreamReader(file.inputStream())
            engine.eval(reader)
        }
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun suspend() {
        TODO("Not yet implemented")
    }

    override fun resume() {
        TODO("Not yet implemented")
    }
}