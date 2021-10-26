package cc.warlock.warlock3.stormfront

import cc.warlock.warlock3.core.text.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TextStream(
    val name: String
) {
    // Line state variables
    private var buffer: StyledString? = null
    private var isPrompting = false
    private var lineStyle: WarlockStyle? = null

    private val _lines = MutableStateFlow<List<StreamLine>>(emptyList())
    val lines = _lines.asStateFlow()

    fun append(text: String, styles: List<WarlockStyle>) {
        styles.forEach {
            if (it.entireLine)
                lineStyle = it
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
            if (it.entireLine)
                lineStyle = it
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
        _lines.value += StreamLine(ignoreWhenBlank = false, style = null, stringFactory = { text })
        isPrompting = false
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
            isPrompting = false
        } else {
            _lines.value += StreamLine(
                ignoreWhenBlank = false,
                style = null,
                stringFactory = { StyledString(command) },
            )
        }
    }

    fun appendEol(ignoreWhenBlank: Boolean): String? {
        if (ignoreWhenBlank && buffer == null)
            return null
        val text = buffer ?: StyledString("")
        _lines.value = _lines.value + StreamLine(
            ignoreWhenBlank = ignoreWhenBlank,
            style = lineStyle,
            stringFactory = { text }
        )
        lineStyle = null
        buffer = null
        isPrompting = false
        return text.toPlainString()
    }

    fun appendPrompt(prompt: String) {
        if (!isPrompting) {
            isPrompting = true
            _lines.value += StreamLine(
                ignoreWhenBlank = false,
                style = null,
                stringFactory = { StyledString(prompt) })
        }
    }
}

data class StreamLine(
    val ignoreWhenBlank: Boolean,
    val style: WarlockStyle?,
    val stringFactory: (Map<String, StyledString>) -> StyledString,
)