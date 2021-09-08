package cc.warlock.warlock3.app.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import cc.warlock.warlock3.core.*
import cc.warlock.warlock3.core.wsl.WslScript
import cc.warlock.warlock3.core.wsl.WslScriptInstance
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class GameViewModel {
    private lateinit var client: StormfrontClient
    private val _lines = MutableStateFlow<List<AnnotatedString>>(emptyList())
    val lines = _lines.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.IO)
    val backgroundColor = MutableStateFlow(Color.DarkGray)
    val textColor = MutableStateFlow(Color.White)
    private val styleColors = MutableStateFlow(
        mapOf(
            "bold" to WarlockColor(red = 0xFF, green = 0xFF, blue = 0x00),
            "error" to WarlockColor(red = 0xFF, green = 0, blue = 0),
        )
    )
    val styleBackgroundColors = MutableStateFlow(
        mapOf("roomName" to WarlockColor(red = 0, green = 0, blue = 0xFF))
    )
    private val scriptInstances = MutableStateFlow<List<ScriptInstance>>(emptyList())

    fun connect(host: String, port: Int, key: String) {
        client = StormfrontClient(host, port)
        scope.launch {
            client.connect(key)
            var buffer: AnnotatedString? = null
            client.eventFlow.collect { event ->
                when (event) {
                    is ClientDataReceivedEvent -> {
                        if (event.stream == null) {
                            val newString = AnnotatedString(
                                text = event.text,
                                spanStyle = flattenStyles(event.styles)?.toSpanStyle() ?: SpanStyle()
                            )
                            buffer = buffer?.plus(newString) ?: newString
                        }
                    }
                    is ClientOutputEvent -> _lines.value = _lines.value + listOf(event.text.toAnnotatedString())
                    is ClientDataSentEvent ->
                        _lines.value = _lines.value + listOf(AnnotatedString(event.text))
                    is ClientDisconnectedEvent ->
                        _lines.value = _lines.value + listOf(AnnotatedString("disconnected"))
                    is ClientEolEvent -> {
                        if (event.stream == null) {
                            _lines.value = _lines.value + listOf(buffer ?: AnnotatedString(""))
                            buffer = null
                        }
                    }
                    is ClientPromptEvent -> {
                        // styleStack.clear()
                    }
                }
            }
        }
    }

    fun send(line: String) {
        if (line.startsWith(".")) {
            val scriptName = line.drop(1)
            val scriptDir = System.getProperty("user.home") + "/.warlock3/scripts"
            val file = File("$scriptDir/$scriptName.wsl")
            if (file.exists()) {
                client.print(StyledString("File exists"))
                val script = WslScript(name = scriptName, file = file)
                val scriptInstance = WslScriptInstance(name = scriptName, script = script)
                scriptInstance.start(client, emptyList())
            } else {
                client.print(StyledString("Could not find a script with that name"))
            }
        } else {
            client.sendCommand(line)
        }
    }

    private fun flattenStyles(styles: List<WarlockStyle>): WarlockStyle? {
        return styles
            .map { completeStyle(it) }
            .reduceOrNull { acc, warlockStyle ->
                acc.mergeWith(warlockStyle)
            }
    }

    private fun completeStyle(style: WarlockStyle): WarlockStyle {
        val name = style.name ?: return style
        return style.copy(
            textColor = style.textColor ?: styleColors.value[name]
        )
    }
}

fun WarlockColor.toColor(): Color {
    return Color(red = red, green = green, blue = blue)
}

fun StyledString.toAnnotatedString(): AnnotatedString {
    return substrings.map { it.toAnnotatedString() }.reduceOrNull { acc, annotatedString ->
        acc + annotatedString
    } ?: AnnotatedString("")
}

fun WarlockStyle.toSpanStyle(): SpanStyle {
    return SpanStyle(
        color = textColor?.toColor() ?: Color.Unspecified,
        background = backgroundColor?.toColor() ?: Color.Unspecified,
        fontFamily = if (monospace) FontFamily.Monospace else null,
        textDecoration = if (underline) TextDecoration.Underline else null,
    )
}

fun StyledStringLeaf.toAnnotatedString(): AnnotatedString {
    return AnnotatedString(text = text, spanStyle = style?.toSpanStyle() ?: SpanStyle())
}