package cc.warlock.warlock3.core.script.wsl

import cc.warlock.warlock3.core.client.WarlockClient
import cc.warlock.warlock3.core.highlights.HighlightRegistry
import cc.warlock.warlock3.core.script.ScriptInstance
import cc.warlock.warlock3.core.script.VariableRegistry
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.core.util.parseArguments
import cc.warlock.warlock3.core.util.toCaseInsensitiveMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class WslScriptInstance(
    override val name: String,
    private val script: WslScript,
    private val variableRegistry: VariableRegistry,
    private val highlightRegistry: HighlightRegistry,
) : ScriptInstance {
    private var _isRunning = false
    override val isRunning: Boolean
        get() = _isRunning
    private lateinit var lines: List<WslLine>
    private val scope = CoroutineScope(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun start(client: WarlockClient, argumentString: String) {
        val arguments = parseArguments(argumentString)
        _isRunning = true
        scope.launch {
            try {
                client.sendCommand("_state scripting on", echo = false)
                lines = script.parse()
                val globalVariables = client.characterId.flatMapLatest { id ->
                    if (id != null) {
                        variableRegistry.getVariablesForCharacter(id).map {
                            it.toCaseInsensitiveMap()
                        }
                    } else {
                        flow {
                            emptyMap<String, String>()
                        }
                    }
                }
                    .stateIn(scope = scope)
                val context = WslContext(
                    client = client,
                    lines = lines,
                    scriptInstance = this@WslScriptInstance,
                    scope = scope,
                    globalVariables = globalVariables,
                    variableRegistry = variableRegistry,
                    highlightRegistry = highlightRegistry,
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
                _isRunning = false
                client.print(StyledString(text = e.reason, styles = listOf(WarlockStyle.Error)))
            } catch (e: WslRuntimeException) {
                _isRunning = false
                client.print(StyledString(text = "Script error: ${e.reason}", styles = listOf(WarlockStyle.Error)))
            } finally {
                client.sendCommand("_state scripting off", echo = false)
            }

            client.print(StyledString("Script has finished: $name"))
        }
    }

    override fun stop() {
        _isRunning = false
        scope.cancel()
    }

    override fun suspend() {
        TODO("Not yet implemented")
    }

    override fun resume() {
        TODO("Not yet implemented")
    }
}