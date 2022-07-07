package cc.warlock.warlock3.stormfront

import cc.warlock.warlock3.core.prefs.defaultMaxScrollLines
import cc.warlock.warlock3.core.text.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TextStream(
    val name: String,
) {
    // Line state variables
    private var buffer: StyledString? = null
    private var isPrompting = false

    private var maxLines = defaultMaxScrollLines

    private val _lines = MutableStateFlow<List<StreamLine>>(emptyList())
    val lines = _lines.asStateFlow()

    private val mutex = Mutex()

    suspend fun append(text: String, styles: List<WarlockStyle>) {
        val newString = StyledString(
            text = text,
            styles = styles
        )
        mutex.withLock {
            buffer = buffer?.plus(newString) ?: newString
            isPrompting = false
        }
    }

    suspend fun appendVariable(name: String, styles: List<WarlockStyle>) {
        val newString = StyledString(
            listOf(
                StyledStringVariable(
                    name = name,
                    styles = styles
                )
            )
        )
        mutex.withLock {
            buffer = buffer?.plus(newString) ?: newString
            isPrompting = false
        }
    }

    suspend fun appendMessage(text: StyledString) {
        mutex.withLock {
            appendLine(StreamLine(ignoreWhenBlank = false, text = text))
            isPrompting = false
        }
    }

    suspend fun appendCommand(command: String) {
        val commandString = StyledString(
            text = command,
            styles = listOf(WarlockStyle.Command),
        )
        mutex.withLock {
            if (isPrompting) {
                val lastLine = _lines.value.last().copy()
                _lines.value = _lines.value.dropLast(1) +
                        lastLine.copy(text = lastLine.text + StyledString(" ") + commandString)
                isPrompting = false
            } else {
                appendLine(
                    StreamLine(
                        ignoreWhenBlank = false,
                        text = commandString,
                    )
                )
            }
        }
    }

    suspend fun appendEol(ignoreWhenBlank: Boolean): String? {
        mutex.withLock {
            if (ignoreWhenBlank && buffer == null)
                return null
            val text = buffer ?: StyledString("")
            appendLine(
                StreamLine(
                    ignoreWhenBlank = ignoreWhenBlank,
                    text = text,
                )
            )
            buffer = null
            isPrompting = false
            return text.toString()
        }
    }

    suspend fun appendPrompt(prompt: String) {
        mutex.withLock {
            if (!isPrompting) {
                isPrompting = true
                appendLine(
                    StreamLine(
                        ignoreWhenBlank = false,
                        text = StyledString(prompt),
                    )
                )
            }
        }
    }

    private fun appendLine(line: StreamLine) {
        val curLines = _lines.value
        _lines.value =
            if (curLines.size >= maxLines) {
                curLines.drop(1)
            } else {
                curLines
            } + line
    }
}

data class StreamLine(
    val ignoreWhenBlank: Boolean,
    val text: StyledString,
)
