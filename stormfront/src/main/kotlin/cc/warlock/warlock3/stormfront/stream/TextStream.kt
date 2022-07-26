package cc.warlock.warlock3.stormfront.stream

import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TextStream(
    val name: String,
    val client: StormfrontClient
) {
    private val maxLines: Int
        get() = client.scrollback.value

    private val _lines = MutableStateFlow<PersistentList<StreamLine>>(persistentListOf())
    val lines = _lines.asStateFlow()

    private val mutex = Mutex()
    private var nextSerialNumber = 0L

    private var isPartial = false

    suspend fun appendPartial(text: StyledString) {
        // TODO: Check max lines
        mutex.withLock {
            if (isPartial) {
                val lastLine = _lines.value.last().copy()
                _lines.value = (_lines.value.dropLast(1) +
                        lastLine.copy(text = lastLine.text + text)).toPersistentList()
            } else {
                isPartial = true
                appendLineLocked(
                    ignoreWhenBlank = false,
                    text = text,
                )
            }
        }
    }

    suspend fun appendEol() {
        mutex.withLock {
            if (!isPartial)
                appendLineLocked(StyledString(""), false)
            isPartial = false
        }
    }

    suspend fun clear() {
        mutex.withLock {
            _lines.value = persistentListOf()
            isPartial = false
        }
    }

    suspend fun appendLine(text: StyledString, ignoreWhenBlank: Boolean = false) {
        mutex.withLock {
            isPartial = false
            appendLineLocked(text, ignoreWhenBlank)
        }
    }

    private fun appendLineLocked(text: StyledString, ignoreWhenBlank: Boolean) {
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
