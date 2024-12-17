package warlockfe.warlock3.compose.ui.window

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.window.StreamLine
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.getComponents
import java.util.*

class ComposeTextStream(
    override val name: String,
    private var maxLines: Int,
    ioDispatcher: CoroutineDispatcher,
) : TextStream {

    private var lines: PersistentList<StreamLine> = persistentListOf()
    private var components: PersistentMap<String, StyledString> = persistentMapOf()

    private val context = ioDispatcher.limitedParallelism(1)
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
        withContext(context) {
            doAppendPartial(text)
        }
    }

    override suspend fun appendPartialAndEol(text: StyledString) {
        withContext(context) {
            doAppendPartial(text)
            isPartial = false
        }
    }

    private fun doAppendPartial(text: StyledString) {
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

    override suspend fun clear() {
        withContext(context) {
            lines = persistentListOf()
            isPartial = false
            usedComponents.clear()
            snapshot.value = StreamSnapshot(
                id = UUID.randomUUID(),
                lines = lines.toPersistentList(),
                components = components.toPersistentMap()
            )
        }
    }

    override suspend fun appendLine(text: StyledString, ignoreWhenBlank: Boolean) {
        // It's possible a component is added that is set prior
        // I don't think this happens in DR, so it's probably OK that we don't handle that case.
        withContext(context) {
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
        lines = currentLines +
                StreamLine(text = text, ignoreWhenBlank = ignoreWhenBlank, serialNumber = nextSerialNumber++)
        snapshot.value = StreamSnapshot(
            id = UUID.randomUUID(),
            lines = lines.toPersistentList(),
            components = components.toPersistentMap()
        )
    }

    override suspend fun updateComponent(name: String, value: StyledString) {
        withContext(context) {
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