package cc.warlock.warlock3.core.wsl

import cc.warlock.warlock3.core.ScriptInstance
import cc.warlock.warlock3.core.StyledString
import cc.warlock.warlock3.core.WarlockClient
import cc.warlock.warlock3.core.util.parseArguments
import cc.warlock.warlock3.stormfront.StyleProvider
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

    override fun start(client: WarlockClient, argumentString: String) {
        val arguments = parseArguments(argumentString)
        _isRunning = true
        scope.launch {
            try {
                lines = script.parse()
                val context = WslContext(client, lines, this@WslScriptInstance)
                context.setVariable("0", WslString(argumentString))
                arguments.forEachIndexed { index, arg ->
                    context.setVariable((index + 1).toString(), WslString(arg))
                }
                while (isRunning) {
                    val line = context.getNextLine()
                    if (line == null) {
                        _isRunning = false
                        client.print(StyledString("Script \"$name\" ended"))
                        break
                    }
                    line.statement.execute(context)
                }
            } catch (e: WslParseException) {
                client.print(StyledString(e.reason, style = StyleProvider.errorStyle))
            } catch (e: WslRuntimeException) {
                _isRunning = false
                client.print(StyledString("Script error: ${e.reason}", StyleProvider.errorStyle))
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