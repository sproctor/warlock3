package cc.warlock.warlock3.app.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import cc.warlock.warlock3.core.ScriptInstance
import cc.warlock.warlock3.core.StyledString
import cc.warlock.warlock3.core.wsl.WslScript
import cc.warlock.warlock3.core.wsl.WslScriptInstance
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.io.File
import kotlin.math.max

class GameViewModel(
    val client: StormfrontClient,
    val lookupMacro: (String) -> String?
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _entryText = mutableStateOf(TextFieldValue())

    val properties: StateFlow<Map<String, String>> = client.properties

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

    private val _sendHistory = mutableStateOf<List<String>>(emptyList())
    val sendHistory: State<List<String>> = _sendHistory

    private val scriptInstances = mutableStateOf<List<ScriptInstance>>(emptyList())

    val windows = client.windows

    val openWindows = client.openWindows

    fun send(line: String) {
        _sendHistory.value = listOf(line) + _sendHistory.value
        if (line.startsWith(".")) {
            val splitCommand = line.drop(1).split(" ", "\t", limit = 2)
            val scriptName = splitCommand.firstOrNull() ?: ""
            val args = splitCommand.getOrNull(1) ?: ""
            val scriptDir = System.getProperty("user.home") + "/.warlock3/scripts"
            val file = File("$scriptDir/$scriptName.wsl")
            if (file.exists()) {
                client.print(StyledString("File exists"))
                val script = WslScript(name = scriptName, file = file)
                val scriptInstance = WslScriptInstance(name = scriptName, script = script)
                scriptInstance.start(client = client, argumentString = args)
                scriptInstances.value += scriptInstance
            } else {
                client.print(StyledString("Could not find a script with that name"))
            }
        } else {
            client.sendCommand(line)
        }
    }

    fun showWindow(name: String) {
        client.showWindow(name)
    }

    fun hideWindow(name: String) {
        client.hideWindow(name)
    }

    fun stopScripts() {
        val count = scriptInstances.value.size
        scriptInstances.value.forEach { scriptInstance ->
            scriptInstance.stop()
        }
        scriptInstances.value = emptyList()
        client.print(StyledString("Stopped $count script(s)"))
    }

    fun handleKeyPress(event: KeyEvent): Boolean {
        val keyString = translateKeyPress(event)
        val macroString = lookupMacro(keyString) ?: return false

        val tokens = tokenizeMacro(macroString) ?: return false

        val initialText = _entryText.value.text
        var selection = _entryText.value.selection
        var resultText =
            tokens.forEach { token ->
                when (token) {

                }
            }
        return true
    }

    fun executeMacro(macro: String) {
        when {
            event.key.keyCode == Key.Enter.keyCode && event.type == KeyEventType.KeyDown -> {
                onSend(textField.text)
                textField = TextFieldValue()
                historyPosition = -1
                true
            }
            event.key.keyCode == Key.DirectionUp.keyCode && event.type == KeyEventType.KeyDown -> {
                if (historyPosition < history.size - 1) {
                    historyPosition++
                    val text = history[historyPosition]
                    textField = TextFieldValue(text = text, selection = TextRange(text.length))
                }
                true
            }
            event.key.keyCode == Key.DirectionDown.keyCode && event.type == KeyEventType.KeyDown -> {
                if (historyPosition > 0) {
                    historyPosition--
                    val text = history[historyPosition]
                    textField = TextFieldValue(text = text, selection = TextRange(text.length))
                }
                true
            }
            event.key.keyCode == Key.Escape.keyCode && event.type == KeyEventType.KeyDown -> {
                stopScripts()
                true
            }
            else -> false
        }
    }

    private fun historyPrev() {

    }

    private fun translateKeyPress(event: KeyEvent): String {
        val keyString = StringBuilder()
        if (event.isCtrlPressed) {
            keyString.append("Ctrl+")
        }
        if (event.isAltPressed) {
            keyString.append("Alt+")
        }
        if (event.isShiftPressed) {
            keyString.append("Shift+")
        }
        if (event.isMetaPressed) {
            keyString.append("Meta+")
        }
        keyString.append(event.key.keyCode)
        return keyString.toString()
    }

    private fun tokenizeMacro(input: String): List<MacroToken>? {
        val tokens = mutableListOf<MacroToken>()
        var state = MacroState.Default
        val buffer = StringBuilder()
        for (i in input.indices) {
            val c = input[i]
            when (state) {
                MacroState.InEntity -> {
                    tokens += MacroEntity(c)
                    state = MacroState.Default
                }
                MacroState.InVariable -> {
                    if (c == '%' && buffer.isEmpty()) {
                        state = MacroState.Default
                        tokens += MacroChar(c)
                        continue
                    } // else
                    if (c.isLetterOrDigit() || c == '_') {
                        buffer.append(c)
                        continue
                    } // else
                    state = MacroState.Default
                    tokens += MacroVariable(buffer.toString())
                    buffer.clear()
                    if (c == '%') {
                        continue
                    }
                }
                MacroState.InCurly -> {
                    if (c == '}') {
                        tokens += MacroCommand(buffer.toString())
                        buffer.clear()
                        state = MacroState.Default
                    } else {
                        buffer.append(c)
                    }
                    continue
                }
                MacroState.Default -> when (c) {
                    '{' -> {
                        state = MacroState.InCurly
                        tokens += MacroString(buffer.toString())
                        buffer.clear()
                    }
                    '%' -> {
                        state = MacroState.InVariable
                        tokens += MacroString(buffer.toString())
                        buffer.clear()
                    }
                    '\\' -> {
                        state = MacroState.InEntity
                        tokens += MacroString(buffer.toString())
                        buffer.clear()
                    }
                    else -> {
                        buffer.append(c)
                    }
                }
            }
        }
    }
}

enum class MacroState {
    Default,
    InEntity,
    InCurly,
    InVariable,
}

sealed class MacroToken
data class MacroChar(val char: Char) : MacroToken()
data class MacroEntity(val char: Char) : MacroToken()
data class MacroCommand(val command: String) : MacroToken()
data class MacroVariable(val name: String) : MacroToken()