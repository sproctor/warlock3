package warlockfe.warlock3.compose.ui.window

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.util.getEntireLineStyles
import warlockfe.warlock3.compose.util.highlight
import warlockfe.warlock3.compose.util.markLinks
import warlockfe.warlock3.compose.util.toAnnotatedString
import warlockfe.warlock3.compose.util.toSpanStyle
import warlockfe.warlock3.compose.util.toTimeString
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.flattenStyles
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.Window
import warlockfe.warlock3.core.window.getComponents
import warlockfe.warlock3.wrayth.util.CompiledAlteration
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ComposeTextStream(
    override val id: String,
    private var maxLines: Int,
    private var markLinks: Boolean,
    private val highlights: StateFlow<List<ViewHighlight>>,
    private val alterations: StateFlow<List<CompiledAlteration>>,
    private val presets: StateFlow<Map<String, StyleDefinition>>,
    private val windows: StateFlow<Map<String, Window>>,
    private val ioDispatcher: CoroutineDispatcher,
    private val soundPlayer: SoundPlayer,
) : TextStream {

    private val cacheLines = ArrayList<CachedLine?>(maxLines)
    private val finishedLines = ArrayList<StreamLine>(maxLines)
    val lines = MutableStateFlow<List<StreamLine>>(emptyList())
    private val components = mutableMapOf<String, StyledString>()

    private var nextSerialNumber = 0L
    private var removedLines = 0L

    private var partialLine: StyledString? = null

    private val componentLocations = mutableMapOf<String, Set<Long>>()

    var actionHandler: ((WarlockAction) -> Unit)? = null

    val mutex = Mutex()

    override suspend fun appendPartial(text: StyledString) {
        mutex.withLock {
            doAppendPartial(text)
        }
    }

    override suspend fun appendPartialAndEol(text: StyledString) {
        mutex.withLock {
            doAppendPartial(text)
            partialLine = null
        }
    }

    private suspend fun doAppendPartial(text: StyledString) {
        if (partialLine == null) {
            partialLine = text
            doAppendLine(text = text, ignoreWhenBlank = false)
        } else {
            val serialNumber = finishedLines.last().serialNumber
            partialLine = partialLine!! + text
            addComponentLocations(text, serialNumber)
            val cachedLine = styledStringToCachedLine(partialLine!!, ignoreWhenBlank = false)
            cacheLines[cacheLines.lastIndex] = cachedLine
            finishedLines[finishedLines.lastIndex] = cachedLineToStreamLine(cachedLine, serialNumber)
            linesUpdated()
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            finishedLines.clear()
            partialLine = null
            componentLocations.clear()
            components.clear()
            nextSerialNumber = 0L
            removedLines = 0L
            cacheLines.clear()
            linesUpdated()
        }
    }

    override suspend fun appendLine(text: StyledString, ignoreWhenBlank: Boolean) {
        mutex.withLock {
            // It's possible a component is added that is set prior
            // I don't think this happens in DR, so it's probably OK that we don't handle that case.
            partialLine = null
            doAppendLine(text, ignoreWhenBlank)
        }
    }

    // Must be called from main thread
    private suspend fun doAppendLine(text: StyledString, ignoreWhenBlank: Boolean) {
        removeLines()
        val serialNumber = nextSerialNumber++
        addComponentLocations(text, serialNumber)
        val cachedLine = styledStringToCachedLine(text, ignoreWhenBlank)
        cacheLines.add(cachedLine)
        val line = cachedLineToStreamLine(cachedLine, serialNumber)
        finishedLines.add(line)
        line.text?.let { playSound(it.text) }
        linesUpdated()
    }

    private fun addComponentLocations(text: StyledString, serialNumber: Long) {
        text.getComponents().forEach { name ->
            val existingLocations = componentLocations[name] ?: emptySet()
            componentLocations[name] = existingLocations + serialNumber
        }
    }

    private fun styledStringToCachedLine(text: StyledString, ignoreWhenBlank: Boolean): CachedLine {
        return CachedLine(
            text = text,
            timestamp = if (windows.value[id]?.showTimestamps ?: false) Clock.System.now() else null,
            ignoreWhenBlank = ignoreWhenBlank,
        )
    }

    private fun removeLines() {
        if (maxLines > 0 && finishedLines.size >= maxLines) {
            finishedLines.removeFirst()
            cacheLines.removeFirst()
            removedLines++
            // TODO: remove componentLocations if their line was removed
        }
    }

    override suspend fun appendResource(url: String) {
        mutex.withLock {
            cacheLines.add(null)
            finishedLines.add(
                StreamImageLine(
                    url = url,
                    serialNumber = nextSerialNumber++,
                )
            )
            linesUpdated()
        }
    }

    override suspend fun updateComponent(name: String, value: StyledString) {
        mutex.withLock {
            components[name] = value
            componentLocations[name]?.forEach { serialNumber ->
                val lineNumber = (serialNumber - removedLines).toInt()
                // If the component has scrolled back past the buffer, ignore it
                if (lineNumber >= 0) {
                    updateLine(lineNumber)
                }
            }
        }
    }

    private fun updateLine(lineNumber: Int) {
        val cachedLine = cacheLines[lineNumber]
        if (cachedLine != null) {
            val serialNumber = finishedLines[lineNumber].serialNumber
            finishedLines[lineNumber] = cachedLineToStreamLine(cachedLine, serialNumber)
            linesUpdated()
        }
    }

    private fun cachedLineToStreamLine(cachedLine: CachedLine, serialNumber: Long): StreamTextLine {
        return cachedLine.toStreamLine(
            serialNumber = serialNumber,
            highlights = highlights.value,
            alterations = alterations.value,
            presets = presets.value,
            components = components,
            actionHandler = { action ->
                actionHandler?.invoke(action)
            },
            markLinks = markLinks,
        )
    }

    private suspend fun playSound(line: String) {
        highlights.value.forEach { highlight ->
            if (highlight.sound != null && highlight.regex.containsMatchIn(line)) {
                withContext(ioDispatcher) {
                    launch {
                        soundPlayer.playSound(highlight.sound)
                    }
                }
            }
        }
    }

    suspend fun setMaxLines(maxLines: Int) {
        mutex.withLock {
            this@ComposeTextStream.maxLines = maxLines
            while (finishedLines.size > maxLines && maxLines > 0) {
                finishedLines.removeFirst()
                cacheLines.removeFirst()
                removedLines++
            }
            linesUpdated()
        }
    }

    fun setMarkLinks(markLinks: Boolean) {
        this.markLinks = markLinks
    }

    private fun linesUpdated() {
        lines.value = finishedLines.toList()
    }
}

@OptIn(ExperimentalTime::class)
data class CachedLine(
    val text: StyledString,
    val timestamp: Instant?,
    val ignoreWhenBlank: Boolean,
) {
    fun toStreamLine(
        serialNumber: Long,
        highlights: List<ViewHighlight>,
        alterations: List<CompiledAlteration>,
        presets: Map<String, StyleDefinition>,
        components: Map<String, StyledString>,
        actionHandler: (WarlockAction) -> Unit,
        markLinks: Boolean,
    ): StreamTextLine {
        return text.toStreamLine(
            ignoreWhenBlank = ignoreWhenBlank,
            serialNumber = serialNumber,
            timestamp = timestamp,
            highlights = highlights,
            alterations = alterations,
            presets = presets,
            components = components,
            actionHandler = actionHandler,
            markLinks = markLinks,
        )
    }
}

@OptIn(ExperimentalTime::class)
fun StyledString.toStreamLine(
    ignoreWhenBlank: Boolean,
    serialNumber: Long,
    timestamp: Instant?,
    highlights: List<ViewHighlight>,
    alterations: List<CompiledAlteration>,
    presets: Map<String, StyleDefinition>,
    components: Map<String, StyledString>,
    actionHandler: (WarlockAction) -> Unit,
    markLinks: Boolean,
): StreamTextLine {
    val text = if (timestamp != null) {
        this + StyledString(" [${timestamp.toTimeString()}]")
    } else {
        this
    }
    val textWithComponents = text.toAnnotatedString(
        variables = components,
        styleMap = presets,
        actionHandler = actionHandler,
    )
        .alter(alterations)
        ?.takeIf { !ignoreWhenBlank || it.isNotBlank() }
    val highlightedResult = textWithComponents?.highlight(highlights)
    val lineStyle = flattenStyles(
        (highlightedResult?.entireLineStyles ?: emptyList()) +
                getEntireLineStyles(
                    variables = components,
                    styleMap = presets,
                )
    )
    return StreamTextLine(
        text = highlightedResult?.let {
            buildAnnotatedString {
                lineStyle?.let { style -> pushStyle(style.toSpanStyle()) }
                append(it.text)
                if (markLinks) {
                    markLinks(highlightedResult, presets)
                }
                if (lineStyle != null) pop()
            }
        },
        entireLineStyle = lineStyle,
        serialNumber = serialNumber,
    )
}

private fun AnnotatedString.alter(alterations: List<CompiledAlteration>): AnnotatedString? {
    var result = this
    alterations.forEach { alteration ->
        val match = alteration.match(result.text)
        if (match != null) {
            result = buildAnnotatedString {
                append(result.take(match.matchResult.range.first))
                match.text?.let { append(it) }
                append(result.substring(match.matchResult.range.last + 1))
            }
            if (result.isEmpty())
                return null
        }
    }
    return result
}
