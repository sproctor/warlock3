package cc.warlock.warlock3.stormfront.stream

import cc.warlock.warlock3.core.prefs.defaultMaxScrollLines
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.StyledStringVariable
import cc.warlock.warlock3.core.text.WarlockStyle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TextStream(
    val name: String,
) {
    // Line state variables
    private var buffer: StyledString? = null
    private var isPrompting = false

    private var maxLines = defaultMaxScrollLines

    private val _lines = MutableStateFlow<PersistentList<StreamLine>>(persistentListOf())
    val lines = _lines.asStateFlow()

    private val mutex = Mutex()
    private var nextSerialNumber = 0L

    suspend fun append(text: StyledString) {
        mutex.withLock {
            buffer = buffer?.plus(text) ?: text
            isPrompting = false
        }
    }

    suspend fun appendVariable(name: String, styles: ImmutableList<WarlockStyle>) {
        val newString = StyledString(
            persistentListOf(
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
            appendLine(ignoreWhenBlank = false, text = text)
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
                _lines.value = (_lines.value.dropLast(1) +
                        lastLine.copy(text = lastLine.text + StyledString(" ") + commandString)).toPersistentList()
                isPrompting = false
            } else {
                appendLine(
                    ignoreWhenBlank = false,
                    text = commandString,
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
                ignoreWhenBlank = ignoreWhenBlank,
                text = text,
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
                    ignoreWhenBlank = false,
                    text = StyledString(prompt),
                )
            }
        }
    }

    fun clear() {
        _lines.value = persistentListOf()
    }

    private fun appendLine(text: StyledString, ignoreWhenBlank: Boolean) {
        val curLines = _lines.value
        _lines.value =
            (if (curLines.size >= maxLines) {
                curLines.drop(1)
            } else {
                curLines
            } + StreamLine(text = text, ignoreWhenBlank = ignoreWhenBlank, serialNumber = nextSerialNumber++))
                .toPersistentList()
    }
}

data class StreamLine(
    val ignoreWhenBlank: Boolean,
    val text: StyledString,
    val serialNumber: Long,
)
