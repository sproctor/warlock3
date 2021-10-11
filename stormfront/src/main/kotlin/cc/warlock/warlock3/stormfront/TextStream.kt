package cc.warlock.warlock3.stormfront

import cc.warlock.warlock3.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TextStream(
    val name: String
) {
    // Line state variables
    private var buffer: StyledString? = null
    private var isPrompting = false
    private var lineBackgroundColor: WarlockColor? = null
    private var waitingCommand: StyledString? = null

    private val _lines = MutableStateFlow<List<StreamLine>>(emptyList())
    val lines = _lines.asStateFlow()

    fun append(text: String, styles: List<WarlockStyle>) {
        styles.forEach {
            if (it.isEntireLineBackground)
                lineBackgroundColor = it.backgroundColor
        }
        val newString = StyledString(
            text = text,
            style = flattenStyles(styles)
        )
        buffer = buffer?.plus(newString) ?: newString
        isPrompting = false
    }

    fun appendVariable(name: String, styles: List<WarlockStyle>) {
        styles.forEach {
            if (it.isEntireLineBackground)
                lineBackgroundColor = it.backgroundColor
        }
        val newString = StyledString(
            listOf(
                StyledStringVariable(
                    name = name,
                    style = flattenStyles(styles)
                )
            )
        )
        buffer = buffer?.plus(newString) ?: newString
        isPrompting = false
    }

    fun appendMessage(text: StyledString) {
        _lines.value = _lines.value + StreamLine(backgroundColor = null, stringFactory = { text })
    }

    fun appendCommand(command: String) {
        val string = StyledString(
            text = command,
            style = StyleProvider.commandStyle,
        )
        if (isPrompting) {
            val lastLine = _lines.value.last().copy()
            _lines.value = _lines.value.dropLast(1) +
                    lastLine.copy(
                        stringFactory = {
                            lastLine.stringFactory(it) + StyledString(" ") + string
                        }
                    )
        } else {
            waitingCommand = string
        }
    }

    fun appendEol() {
        val text = buffer ?: StyledString("")
        _lines.value = _lines.value + StreamLine(
            backgroundColor = lineBackgroundColor,
            stringFactory = { text }
        )
        lineBackgroundColor = null
        buffer = null
        isPrompting = false
    }

    fun appendPrompt(prompt: String) {
        if (!isPrompting || waitingCommand != null) {
            isPrompting = true
            val text = if (waitingCommand != null) {
                StyledString("$prompt ") + waitingCommand!!
            } else {
                StyledString(prompt)
            }
            _lines.value = _lines.value + StreamLine(backgroundColor = null, stringFactory = { text })
        }
    }
}

data class StreamLine(
    val backgroundColor: WarlockColor?,
    val stringFactory: (Map<String, StyledString>) -> StyledString,
)