package cc.warlock.warlock3.app.ui.window

import androidx.compose.runtime.mutableStateOf
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.window.StreamLine
import cc.warlock.warlock3.core.window.TextStream
import cc.warlock.warlock3.core.window.getComponents
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class ComposeTextStream(
    override val name: String,
    private var maxLines: Int
) : TextStream {

    private val lines = ArrayList<StreamLine>(maxLines)
    private val components = HashMap<String, StyledString>()

    private val mutex = Mutex()
    private var nextSerialNumber = 0L

    private var isPartial = false

    private val usedComponents = HashSet<String>()

    val snapshot = mutableStateOf(
        StreamSnapshot(
            id = UUID.randomUUID(),
            lines = persistentListOf(),
            components = persistentMapOf()
        )
    )

    override suspend fun appendPartial(text: StyledString) {
        mutex.withLock {
            if (isPartial) {
                val lastLine = lines.last()
                lines[lines.lastIndex] = lastLine.copy(text = lastLine.text + text)
            } else {
                isPartial = true
                doAppendLine(
                    ignoreWhenBlank = false,
                    text = text,
                )
            }
        }
    }

    override suspend fun appendEol() {
        mutex.withLock {
            if (!isPartial)
                doAppendLine(StyledString(""), false)
            isPartial = false
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            lines.clear()
            isPartial = false
            usedComponents.clear()
        }
    }

    override suspend fun appendLine(text: StyledString, ignoreWhenBlank: Boolean) {
        mutex.withLock {
            isPartial = false
            doAppendLine(text, ignoreWhenBlank)
        }
    }

    // Must be called with lock held
    private fun doAppendLine(text: StyledString, ignoreWhenBlank: Boolean) {
        usedComponents += text.getComponents()
        if (maxLines > 0 && lines.size >= maxLines) {
            lines.removeFirst()
        }
        lines += StreamLine(text = text, ignoreWhenBlank = ignoreWhenBlank, serialNumber = nextSerialNumber++)
        snapshot.value = StreamSnapshot(
            id = UUID.randomUUID(),
            lines = lines.toPersistentList(),
            components = components.toPersistentMap()
        )
    }

    override suspend fun updateComponent(name: String, value: StyledString) {
        mutex.withLock {
            if (usedComponents.contains(name)) {
                components[name] = value
                snapshot.value = StreamSnapshot(
                    id = UUID.randomUUID(),
                    lines = lines.toPersistentList(),
                    components = components.toPersistentMap()
                )
            }
        }
    }
}