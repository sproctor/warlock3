package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.window.StreamLine
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.getComponents
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ComposeTextStream(
    override val id: String,
    private var maxLines: Int,
    private val mainDispatcher: CoroutineDispatcher,
) : TextStream {

    val lines = mutableStateListOf<StreamLine>()
    val components = mutableStateMapOf<String, StyledString>()

    private var nextSerialNumber = 0L

    private var isPartial = false

    private val usedComponents = HashSet<String>()

    override suspend fun appendPartial(text: StyledString) {
        withContext(mainDispatcher) {
            doAppendPartial(text)
        }
    }

    override suspend fun appendPartialAndEol(text: StyledString) {
        withContext(mainDispatcher) {
            doAppendPartial(text)
            isPartial = false
        }
    }

    private fun doAppendPartial(text: StyledString) {
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

    override suspend fun clear() {
        withContext(mainDispatcher) {
            lines.clear()
            isPartial = false
            usedComponents.clear()
        }
    }

    override suspend fun appendLine(text: StyledString, ignoreWhenBlank: Boolean) {
        // It's possible a component is added that is set prior
        // I don't think this happens in DR, so it's probably OK that we don't handle that case.
        withContext(mainDispatcher) {
            isPartial = false
            doAppendLine(text, ignoreWhenBlank)
        }
    }

    // Must be called with lock held
    private fun doAppendLine(text: StyledString, ignoreWhenBlank: Boolean) {
        usedComponents += text.getComponents()
        if (maxLines > 0 && lines.size >= maxLines) {
            lines.removeAt(0)
        }
        lines.add(
            StreamLine(
                text = text,
                ignoreWhenBlank = ignoreWhenBlank,
                serialNumber = nextSerialNumber++,
                timestamp = Clock.System.now(),
            )
        )
    }

    override suspend fun updateComponent(name: String, value: StyledString) {
        withContext(mainDispatcher) {
            if (usedComponents.contains(name)) {
                components[name] = value
            }
        }
    }

    fun setMaxLines(maxLines: Int) {
        this.maxLines = maxLines
        // TODO: test this method
        if (lines.size > maxLines) {
            lines.removeRange(maxLines - 1, lines.lastIndex)
        }
    }
}