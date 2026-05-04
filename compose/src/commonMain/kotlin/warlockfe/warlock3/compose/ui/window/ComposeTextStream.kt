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
    private var showImages: Boolean,
    private var showTimestamps: Boolean,
    private val highlights: StateFlow<List<ViewHighlight>>,
    private val alterations: StateFlow<List<CompiledAlteration>>,
    private val presets: StateFlow<Map<String, StyleDefinition>>,
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

    override suspend fun appendPartial(
        text: StyledString,
        isPrompt: Boolean,
    ) {
        mutex.withLock {
            doAppendPartial(text, isPrompt)
        }
    }

    override suspend fun appendPartialAndEol(text: StyledString) {
        mutex.withLock {
            doAppendPartial(text, isPrompt = false)
            partialLine = null
        }
    }

    private suspend fun doAppendPartial(
        text: StyledString,
        isPrompt: Boolean,
    ) {
        if (partialLine == null) {
            partialLine = text
            doAppendLine(text = text, ignoreWhenBlank = false, showWhenClosed = null, isPrompt = isPrompt)
        } else {
            val serialNumber = finishedLines.last().serialNumber
            partialLine = partialLine!! + text
            addComponentLocations(text, serialNumber)
            val cachedLine =
                styledStringToCachedLine(
                    text = partialLine!!,
                    ignoreWhenBlank = false,
                    showWhenClosed = null,
                    isPrompt = isPrompt,
                )
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

    override suspend fun appendLine(
        text: StyledString,
        ignoreWhenBlank: Boolean,
        showWhenClosed: String?,
    ) {
        mutex.withLock {
            // It's possible a component is added that is set prior
            // I don't think this happens in DR, so it's probably OK that we don't handle that case.
            partialLine = null
            doAppendLine(text, ignoreWhenBlank, showWhenClosed, isPrompt = false)
        }
    }

    // Must be called from main thread
    private suspend fun doAppendLine(
        text: StyledString,
        ignoreWhenBlank: Boolean,
        showWhenClosed: String?,
        isPrompt: Boolean,
    ) {
        removeLines()
        val serialNumber = nextSerialNumber++
        addComponentLocations(text, serialNumber)
        val cachedLine = styledStringToCachedLine(text, ignoreWhenBlank, showWhenClosed, isPrompt)
        cacheLines.add(cachedLine)
        val line = cachedLineToStreamLine(cachedLine, serialNumber)
        finishedLines.add(line)
        line.text?.let { playSound(it.text) }
        linesUpdated()
    }

    private fun addComponentLocations(
        text: StyledString,
        serialNumber: Long,
    ) {
        text.getComponents().forEach { name ->
            val existingLocations = componentLocations[name] ?: emptySet()
            componentLocations[name] = existingLocations + serialNumber
        }
    }

    private fun styledStringToCachedLine(
        text: StyledString,
        ignoreWhenBlank: Boolean,
        showWhenClosed: String?,
        isPrompt: Boolean,
    ): CachedLine =
        CachedLine(
            text = text,
            timestamp = Clock.System.now(),
            ignoreWhenBlank = ignoreWhenBlank,
            showWhenClosed = showWhenClosed,
            isPrompt = isPrompt,
        )

    private fun removeLines() {
        while (maxLines > 0 && finishedLines.size >= maxLines) {
            finishedLines.removeFirst()
            cacheLines.removeFirst()
            removedLines++
            // Intentionally leak components here. They don't exist in the main window,
            // and no other windows get long enough
        }
    }

    override suspend fun appendResource(url: String) {
        // Images must be on their own line
        partialLine = null
        if (!showImages) return
        mutex.withLock {
            cacheLines.add(null)
            finishedLines.add(
                StreamImageLine(
                    url = url,
                    serialNumber = nextSerialNumber++,
                ),
            )
            linesUpdated()
        }
    }

    override suspend fun updateComponent(
        name: String,
        value: StyledString,
    ) {
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

    private fun cachedLineToStreamLine(
        cachedLine: CachedLine,
        serialNumber: Long,
    ): StreamTextLine =
        cachedLine.toStreamLine(
            streamName = id,
            showTimestamp = showTimestamps,
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
            removeLines()
            linesUpdated()
        }
    }

    fun setMarkLinks(markLinks: Boolean) {
        this.markLinks = markLinks
    }

    fun setShowImages(showImages: Boolean) {
        this.showImages = showImages
    }

    private fun linesUpdated() {
        lines.value = finishedLines.toList()
    }

    override fun showTimestamps(value: Boolean) {
        showTimestamps = value
    }
}

@OptIn(ExperimentalTime::class)
data class CachedLine(
    val text: StyledString,
    val timestamp: Instant,
    val ignoreWhenBlank: Boolean,
    val showWhenClosed: String?,
    val isPrompt: Boolean,
) {
    fun toStreamLine(
        streamName: String,
        showTimestamp: Boolean,
        serialNumber: Long,
        highlights: List<ViewHighlight>,
        alterations: List<CompiledAlteration>,
        presets: Map<String, StyleDefinition>,
        components: Map<String, StyledString>,
        actionHandler: (WarlockAction) -> Unit,
        markLinks: Boolean,
    ): StreamTextLine =
        text.toStreamLine(
            streamName = streamName,
            showTimestamp = showTimestamp,
            ignoreWhenBlank = ignoreWhenBlank,
            serialNumber = serialNumber,
            isPrompt = isPrompt,
            timestamp = timestamp,
            highlights = highlights,
            alterations = alterations,
            presets = presets,
            components = components,
            actionHandler = actionHandler,
            markLinks = markLinks,
            showWhenClosed = showWhenClosed,
        )
}

@OptIn(ExperimentalTime::class)
fun StyledString.toStreamLine(
    streamName: String,
    showTimestamp: Boolean,
    ignoreWhenBlank: Boolean,
    serialNumber: Long,
    showWhenClosed: String?,
    isPrompt: Boolean,
    timestamp: Instant,
    highlights: List<ViewHighlight>,
    alterations: List<CompiledAlteration>,
    presets: Map<String, StyleDefinition>,
    components: Map<String, StyledString>,
    actionHandler: (WarlockAction) -> Unit,
    markLinks: Boolean,
): StreamTextLine {
    val text =
        if (showTimestamp) {
            this + StyledString(" [${timestamp.toTimeString()}]")
        } else {
            this
        }
    val textWithComponents =
        text
            .toAnnotatedString(
                variables = components,
                styleMap = presets,
                actionHandler = actionHandler,
            ).alter(alterations, streamName)
            ?.takeIf { !ignoreWhenBlank || it.isNotBlank() }
    val textWithLinks =
        textWithComponents?.let { content ->
            if (markLinks) {
                buildAnnotatedString {
                    append(content)
                    markLinks(content, presets)
                }
            } else {
                content
            }
        }
    val highlightedResult = textWithLinks?.highlight(highlights)
    val lineStyle =
        flattenStyles(
            (highlightedResult?.entireLineStyles ?: emptyList()) +
                getEntireLineStyles(
                    variables = components,
                    styleMap = presets,
                ),
        )
    return StreamTextLine(
        text =
            highlightedResult?.let {
                buildAnnotatedString {
                    lineStyle?.let { style -> pushStyle(style.toSpanStyle()) }
                    append(it.text)
                    if (lineStyle != null) pop()
                }
            },
        entireLineStyle = lineStyle,
        serialNumber = serialNumber,
        showWhenClosed = showWhenClosed,
        isPrompt = isPrompt,
    )
}

private fun AnnotatedString.alter(
    alterations: List<CompiledAlteration>,
    streamName: String,
): AnnotatedString? {
    // Ignore blank lines
    if (alterations.isEmpty() || text.isEmpty()) {
        return this
    }
    var result = this
    alterations.forEach { alteration ->
        if (alteration.appliesToStream(streamName)) {
            try {
                result =
                    result.replaceWithRegex(
                        pattern = alteration.regex,
                        replacement = alteration.replacement ?: "",
                    )
            } catch (_: Exception) {
                // Ignore it
            }
        }
    }
    // Skip the line if it was blanked
    return result.takeIf { it.text.isNotEmpty() }
}

fun AnnotatedString.replaceWithRegex(
    pattern: Regex,
    replacement: String,
): AnnotatedString =
    buildAnnotatedString {
        var remaining = this@replaceWithRegex
        while (true) {
            val match = pattern.find(remaining.text) ?: break

            // Append everything before the match, preserving spans
            append(remaining.subSequence(0, match.range.first))

            // Use replaceFirst to get the full replaced string, then extract
            // just the replacement portion using the known before/after lengths
            val replaced = pattern.replaceFirst(remaining.text, replacement)
            val afterLen = remaining.text.length - (match.range.last + 1)
            append(replaced.substring(match.range.first, replaced.length - afterLen))

            // Advance past the match
            remaining = remaining.subSequence(match.range.last + 1, remaining.length)
        }
        // Append the tail after the final match
        append(remaining)
    }
