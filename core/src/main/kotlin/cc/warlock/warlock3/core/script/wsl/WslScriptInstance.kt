package cc.warlock.warlock3.core.script.wsl

import cc.warlock.warlock3.core.client.WarlockClient
import cc.warlock.warlock3.core.script.ScriptInstance
import cc.warlock.warlock3.core.script.VariableRegistry
import cc.warlock.warlock3.core.text.StyleProvider
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.util.parseArguments
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

    override fun start(client: WarlockClient, argumentString: String, variableRegistry: VariableRegistry) {
        val arguments = parseArguments(argumentString)
        _isRunning = true
        scope.launch {
            try {
                lines = script.parse()
                val context = WslContext(
                    client = client,
                    lines = lines,
                    scriptInstance = this@WslScriptInstance,
                    variableRegistry = variableRegistry
                )
                context.setScriptVariable("0", WslString(argumentString))
                arguments.forEachIndexed { index, arg ->
                    context.setScriptVariable((index + 1).toString(), WslString(arg))
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