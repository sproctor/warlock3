package cc.warlock.warlock3.app.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import cc.warlock.warlock3.core.*
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import cc.warlock.warlock3.stormfront.protocol.StormfrontNodeVisitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.*

class GameViewModel {
    private lateinit var client: StormfrontClient
    private val _lines = MutableStateFlow<List<AnnotatedString>>(emptyList())
    val lines = _lines.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.IO)
    val backgroundColor = MutableStateFlow(Color.DarkGray)
    val textColor = MutableStateFlow(Color.White)
    val styleColors = MutableStateFlow(
        mapOf(
            "bold" to WarlockColor(red = 0xFF, green = 0xFF, blue = 0x00)
        )
    )
    val styleBackgroundColors = MutableStateFlow(
        mapOf("roomName" to WarlockColor(red = 0, green = 0, blue = 0xFF))
    )
    private val styleStack = Stack<WarlockStyle>()
    private var outputStyle: WarlockStyle? = null

    fun connect(host: String, port: Int, key: String) {
        client = StormfrontClient(host, port)
        scope.launch {
            client.connect(key)
            var buffer: AnnotatedString? = null
            var currentStream: String? = null
            client.eventFlow.collect { event ->
                when (event) {
                    is ClientDataReceivedEvent -> {
                        if (currentStream == null) {
                            val newString = AnnotatedString(
                                text = event.text,
                                spanStyle = getCurrentStyle()?.toSpanStyle() ?: SpanStyle()
                            )
                            buffer = buffer?.plus(newString) ?: newString
                        }
                    }
                    is ClientOutputEvent -> _lines.value = _lines.value + listOf(event.text.toAnnotatedString())
                    is ClientDataSentEvent ->
                        _lines.value = _lines.value + listOf(AnnotatedString(event.text))
                    is ClientDisconnectedEvent ->
                        _lines.value = _lines.value + listOf(AnnotatedString("disconnected"))
                    ClientEolEvent -> {
                        if (currentStream == null) {
                            _lines.value = _lines.value + listOf(buffer ?: AnnotatedString(""))
                            buffer = null
                        }
                    }
                    is ClientStreamChangedEvent -> currentStream = event.stream
                    is ClientPromptEvent -> {
                        styleStack.clear()
                    }
                    is ClientAddStyleEvent -> styleStack.push(event.style)
                    is ClientRemoveStyleEvent -> {
                        if (styleStack.isNotEmpty() && styleStack.peek() == event.style) {
                            styleStack.pop()
                        }
                    }
                    ClientClearStyleEvent -> styleStack.clear()
                    is ClientOutputStyleEvent -> outputStyle = event.style
                }
            }
        }
    }

    fun send(line: String) {
        client.sendCommand(line)
    }

    private fun getCurrentStyle(): WarlockStyle? {
        val style = styleStack
            .map { completeStyle(it) }
            .reduceOrNull { acc, warlockStyle ->
                acc.mergeWith(warlockStyle)
            } ?: return outputStyle
        return outputStyle?.mergeWith(style) ?: style
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
    val style = SpanStyle(
        color = textColor?.toColor() ?: Color.Unspecified,
        background = backgroundColor?.toColor() ?: Color.Unspecified,
        fontFamily = if (monospace) FontFamily.Monospace else null,
        textDecoration = if (underline) TextDecoration.Underline else null,
    )
    return style
}

fun StyledStringLeaf.toAnnotatedString(): AnnotatedString {
    return AnnotatedString(text = text, spanStyle = style?.toSpanStyle() ?: SpanStyle())
}