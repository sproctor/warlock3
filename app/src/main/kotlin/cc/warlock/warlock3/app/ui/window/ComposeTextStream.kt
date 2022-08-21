package cc.warlock.warlock3.app.ui.window

import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.window.StreamLine
import cc.warlock.warlock3.core.window.TextStream
import cc.warlock.warlock3.core.window.getComponents
import kotlinx.collections.immutable.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class ComposeTextStream(
    override val name: String,
    private var maxLines: Int
) : TextStream {

    private var lines: PersistentList<StreamLine> = persistentListOf()
    private var components: PersistentMap<String, StyledString> = persistentMapOf()

    private val mutex = Mutex()
    private var nextSerialNumber = 0L

    private var isPartial = false

    private val usedComponents = HashSet<String>()

    val snapshot = MutableStateFlow(
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
                lines = lines.set(lines.lastIndex, lastLine.copy(text = lastLine.text + text))
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
            lines = persistentListOf()
            isPartial = false
            usedComponents.clear()
        }
    }

    override suspend fun appendLine(text: StyledString, ignoreWhenBlank: Boolean) {
        // It's possible a component is added that is set prior
        // I don't think this happens in DR, so it's probably OK that we don't handle that case.
        mutex.withLock {
            isPartial = false
            doAppendLine(text, ignoreWhenBlank)
        }
    }

    // Must be called with lock held
    private fun doAppendLine(text: StyledString, ignoreWhenBlank: Boolean) {
        usedComponents += text.getComponents()
        val currentLines = if (maxLines > 0 && lines.size >= maxLines) {
            lines.removeAt(0)
        } else {
            lines
        }
        lines = currentLines + StreamLine(text = text, ignoreWhenBlank = ignoreWhenBlank, serialNumber = nextSerialNumber++)
        snapshot.value = StreamSnapshot(
            id = UUID.randomUUID(),
            lines = lines.toPersistentList(),
            components = components.toPersistentMap()
        )
    }

    override suspend fun updateComponent(name: String, value: StyledString) {
        mutex.withLock {
            if (usedComponents.contains(name)) {
                components += name to value
                snapshot.value = StreamSnapshot(
                    id = UUID.randomUUID(),
                    lines = lines,
                    components = components
                )
            }
        }
    }
}