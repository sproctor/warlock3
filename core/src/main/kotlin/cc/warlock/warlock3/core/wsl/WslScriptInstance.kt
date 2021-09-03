package cc.warlock.warlock3.core.wsl

import cc.warlock.warlock3.core.ScriptInstance
import cc.warlock.warlock3.core.StyledString
import cc.warlock.warlock3.core.WarlockClient
import cc.warlock.warlock3.core.WarlockStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WslScriptInstance(
    override val name: String,
    private val script: WslScript,
) : ScriptInstance {
    private var _isRunning = false
    override val isRunning: Boolean
        get() = _isRunning
    private lateinit var lines: List<WslLine>
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun start(client: WarlockClient, arguments: List<String>) {
        _isRunning = true
        scope.launch {
            try {
                lines = script.parse()
                val context = WslContext(client)
                arguments.forEachIndexed { index, arg ->
                    context.setVariable((index + 1).toString(), WslValue.WslString(arg))
                }
                while (isRunning) {
                    val lineNumber = context.getCurrentLine()
                    if (lineNumber >= lines.size) {
                        _isRunning = false
                        client.print(StyledString("Script \"$name\" ended"))
                        break
                    }
                    val line = lines[lineNumber]
                    line.statement.execute(context)
                    context.incrementLine()
                }
            } catch (e: WslParseException) {
                client.print(StyledString(e.reason, WarlockStyle(name = "error")))
            }
        }
    }

    override fun stop() {
        _isRunning = false
    }

    override fun suspend() {
        TODO("Not yet implemented")
    }

    override fun resume() {
        TODO("Not yet implemented")
    }
}