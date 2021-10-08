package cc.warlock.warlock3.app.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import cc.warlock.warlock3.app.model.ViewLine
import cc.warlock.warlock3.core.*
import cc.warlock.warlock3.stormfront.StyleProvider
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

class WindowViewModel(
    val name: String,
    private val showPrompts: Boolean,
    private val openWindows: StateFlow<List<String>>,
    client: StormfrontClient,
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // Line state variables
    private var buffer: AnnotatedString? = null
    private var isPrompting = false
    private var lineBackgroundColor: Color? = null
    private var waitingCommand: AnnotatedString? = null

    private val _lines = MutableStateFlow<List<ViewLine>>(emptyList())
    val lines = _lines.asStateFlow()

    val components = client.components

    val backgroundColor = MutableStateFlow(Color.DarkGray)
    val textColor = MutableStateFlow(Color.White)

    init {
        client.eventFlow.onEach { event ->
            when (event) {
                is ClientDataReceivedEvent -> {
                    val streamName = event.stream ?: "main"
                    val window = client.windows.value[streamName]
                    val isOpen = openWindows.value.contains(streamName)
                    val ifClosedTarget = window?.ifClosed ?: "main"
                    if (name == streamName || (!isOpen && ifClosedTarget == name)) {
                        event.styles.forEach {
                            if (it.isEntireLineBackground)
                                lineBackgroundColor = it.backgroundColor?.toColor()
                        }
                        val newString = AnnotatedString(
                            text = event.text,
                            spanStyle = flattenStyles(event.styles)?.toSpanStyle() ?: SpanStyle()
                        )
                        buffer = buffer?.plus(newString) ?: newString
                        isPrompting = false
                    }
                }
                is ClientOutputEvent -> _lines.value = _lines.value +
                        ViewLine(backgroundColor = null, stringFactory = { event.text.toAnnotatedString() })
                is ClientCommandEvent -> {
                    val command = AnnotatedString(
                        text = event.text,
                        spanStyle = StyleProvider.commandStyle.toSpanStyle(),
                    )
                    if (isPrompting) {
                        val lastLine = _lines.value.last().copy()
                        _lines.value = _lines.value.dropLast(1) +
                                lastLine.copy(
                                    stringFactory = {
                                        lastLine.stringFactory(it) + AnnotatedString(" ") + command
                                    }
                                )
                    } else {
                        waitingCommand = command
                    }
                }
                is ClientDisconnectedEvent ->
                    _lines.value = _lines.value + ViewLine(
                        backgroundColor = null,
                        stringFactory = { AnnotatedString("Connection was closed by the server.") }
                    )
                is ClientEolEvent -> {
                    if (event.stream == null) {
                        val text = buffer ?: AnnotatedString("")
                        _lines.value = _lines.value + ViewLine(
                            backgroundColor = lineBackgroundColor,
                            stringFactory = { text }
                        )
                        lineBackgroundColor = null
                        buffer = null
                        isPrompting = false
                    }
                }
                is ClientPromptEvent -> {
                    if (showPrompts && (!isPrompting || waitingCommand != null)) {
                        isPrompting = true
                        val text = if (waitingCommand != null) {
                            AnnotatedString(event.prompt + " ") + waitingCommand!!
                        } else {
                            AnnotatedString(event.prompt)
                        }
                        _lines.value = _lines.value + ViewLine(backgroundColor = null, stringFactory = { text })
                    }
                }
                is ClientProgressBarEvent -> {
                    // don't care
                }
                is ClientCompassEvent -> {
                    // don't care
                }
            }
        }
            .launchIn(scope)
    }
}