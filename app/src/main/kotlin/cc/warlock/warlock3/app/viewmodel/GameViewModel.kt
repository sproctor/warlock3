package cc.warlock.warlock3.app.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import cc.warlock.warlock3.app.macros.macroCommands
import cc.warlock.warlock3.core.highlights.HighlightRegistry
import cc.warlock.warlock3.core.macros.MacroRepository
import cc.warlock.warlock3.core.parser.MacroLexer
import cc.warlock.warlock3.core.script.ScriptInstance
import cc.warlock.warlock3.core.script.VariableRegistry
import cc.warlock.warlock3.core.script.wsl.WslScript
import cc.warlock.warlock3.core.script.wsl.WslScriptInstance
import cc.warlock.warlock3.core.text.StyleRegistry
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.window.WindowRegistry
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.Token
import java.io.File
import kotlin.math.max

class GameViewModel(
    windowRegistry: WindowRegistry,
    val client: StormfrontClient,
    val macroRepository: MacroRepository,
    val variableRegistry: VariableRegistry,
    val highlightRegistry: HighlightRegistry,
    val styleRegistry: StyleRegistry,
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _entryText = mutableStateOf(TextFieldValue())
    val entryText: State<TextFieldValue> = _entryText

    private val storedText = mutableStateOf<String?>(null)

    val properties: StateFlow<Map<String, String>> = client.properties

    val variables = combine(client.characterId, variableRegistry.variables) { characterId, allVariables ->
        characterId?.let { allVariables[it] } ?: emptyMap()
    }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyMap())

    private val currentTime: Flow<Int> = flow {
        while (true) {
            val time = client.time
            emit((time / 1000L).toInt())
            val nextSecond = 1000L - (time % 1000)
            delay(max(10L, nextSecond))
        }
    }

    val roundTime = combine(currentTime, properties) { currentTime, properties ->
        val roundEnd = properties["roundtime"]?.toIntOrNull() ?: 0
        max(0, roundEnd - currentTime)
    }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = 0)

    val castTime = combine(currentTime, properties) { currentTime, properties ->
        val roundEnd = properties["casttime"]?.toIntOrNull() ?: 0
        max(0, roundEnd - currentTime)
    }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = 0)

    private var historyPosition = -1
    private val _sendHistory = mutableStateOf<List<String>>(emptyList())
    val sendHistory: State<List<String>> = _sendHistory

    private val scriptInstances = mutableStateOf<List<ScriptInstance>>(emptyList())

    val windows = windowRegistry.windows

    val openWindows = windowRegistry.openWindows

    fun submit() {
        val line = _entryText.value.text
        _entryText.value = TextFieldValue()
        _sendHistory.value = listOf(line) + _sendHistory.value
        historyPosition = -1
        if (line.startsWith(".")) {
            val splitCommand = line.drop(1).split(" ", "\t", limit = 2)
            val scriptName = splitCommand.firstOrNull() ?: ""
            val args = splitCommand.getOrNull(1) ?: ""
            val scriptDir = System.getProperty("user.home") + "/.warlock3/scripts"
            val file = File("$scriptDir/$scriptName.wsl")
            if (file.exists()) {
                client.print(StyledString("File exists"))
                val script = WslScript(name = scriptName, file = file)
                val scriptInstance = WslScriptInstance(
                    name = scriptName,
                    script = script,
                    variableRegistry = variableRegistry,
                    highlightRegistry = highlightRegistry,
                    styleRegistry = styleRegistry,
                )
                scriptInstance.start(client = client, argumentString = args)
                scriptInstances.value += scriptInstance
            } else {
                client.print(StyledString("Could not find a script with that name"))
            }
        } else {
            client.sendCommand(line)
        }
    }

    fun stopScripts() {
        val count = scriptInstances.value.size
        scriptInstances.value.forEach { scriptInstance ->
            scriptInstance.stop()
        }
        scriptInstances.value = emptyList()
        client.print(StyledString("Stopped $count script(s)"))
    }

    @OptIn(ExperimentalComposeUiApi::class)
    fun handleKeyPress(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) {
            return false
        }
        if (event.key.keyCode == Key.Enter.keyCode) {
            submit()
            return true
        }

        val keyString = translateKeyPress(event)
        val macroString = client.characterId.value?.let { macroRepository.getMacro(it, keyString) } ?: return false

        val tokens = tokenizeMacro(macroString) ?: return false

        executeMacro(tokens)

        return true
    }

    private fun executeMacro(tokens: List<Token>) {
        scope.launch {
            tokens.forEach { token ->
                when (token.type) {
                    MacroLexer.Entity -> {
                        val entity = token.text
                        assert(entity.length == 2)
                        assert(entity[0] == '\\')
                        handleEntity(entity[1])
                    }
                    MacroLexer.At -> {
                        _entryText.value = _entryText.value.copy(selection = TextRange(_entryText.value.text.length))
                    }
                    MacroLexer.Question -> {
                        storedText.value?.let { entryAppend(it) }
                    }
                    MacroLexer.Character -> {
                        entryAppend(token.text)
                    }
                    MacroLexer.VariableName -> {
                        token.text?.let { if (it.endsWith("%")) it.drop(1) else it }
                            ?.let { name ->
                                entryAppend(variables.value[name] ?: "")
                            }
                    }
                    MacroLexer.CommandText -> {
                        val command = macroCommands[token.text.lowercase()]
                        command?.invoke(this@GameViewModel)
                    }
                }
            }
        }
    }

    private suspend fun handleEntity(entity: Char) {
        when (entity) {
            'x' -> {
                storedText.value = _entryText.value.text
                entryClear()
            }
            'r' -> {
                submit()
            }
            'p' -> {
                delay(1_000L)
            }
        }
    }

    private fun entryClear() {
        _entryText.value = TextFieldValue()
    }

    private fun entryAppend(text: String) {
        _entryText.value = _entryText.value.copy(text = _entryText.value.text + text)
    }

    fun historyPrev() {
        val history = sendHistory.value
        if (historyPosition < history.size - 1) {
            historyPosition++
            val text = history[historyPosition]
            _entryText.value = TextFieldValue(text = text, selection = TextRange(text.length))
        }
    }

    fun historyNext() {
        if (historyPosition >= 0) {
            historyPosition--
            if (historyPosition < 0) {
                entryClear()
            } else {
                val text = sendHistory.value[historyPosition]
                _entryText.value = TextFieldValue(text = text, selection = TextRange(text.length))
            }
        }
    }

    private fun translateKeyPress(event: KeyEvent): String {
        val keyString = StringBuilder()
        if (event.isCtrlPressed) {
            keyString.append("ctrl+")
        }
        if (event.isAltPressed) {
            keyString.append("alt+")
        }
        if (event.isShiftPressed) {
            keyString.append("shift+")
        }
        if (event.isMetaPressed) {
            keyString.append("meta+")
        }
        keyString.append(event.key.keyCode)
        return keyString.toString()
    }

    private fun tokenizeMacro(input: String): List<Token> {
        val charStream = CharStreams.fromString(input)
        val lexer = MacroLexer(charStream)
        return lexer.allTokens
    }

    fun setEntryText(value: TextFieldValue) {
        _entryText.value = value
    }
}
