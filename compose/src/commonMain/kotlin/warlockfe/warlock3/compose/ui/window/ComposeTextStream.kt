package warlockfe.warlock3.compose.ui.window

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.util.AnnotatedStringHighlightResult
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.TimeSource

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
    private val soundPlayer: SoundPlayer,
    private val workQueue: StreamWorkQueue,
    private val scope: CoroutineScope,
) : TextStream {
    private val cacheLines = ArrayList<CachedLine?>(maxLines)
    private val finishedLines = ArrayList<StreamLine>(maxLines)
    val lines = MutableStateFlow<List<StreamLine>>(emptyList())
    private val components = mutableMapOf<String, StyledString>()

    private var nextSerialNumber = 0L
    private var removedLines = 0L

    private var applyStyling: Boolean = true

    private var partialLine: StyledString? = null

    private val componentLocations = mutableMapOf<String, Set<Long>>()

    var actionHandler: ((WarlockAction) -> Unit)? = null

    val mutex = Mutex()

    override suspend fun appendPartial(
        text: StyledString,
        isPrompt: Boolean,
    ) {
        workQueue.submit {
            mutex.withLock {
                doAppendPartial(text, isPrompt)
            }
        }
    }

    override suspend fun appendPartialAndEol(text: StyledString) {
        workQueue.submit {
            mutex.withLock {
                doAppendPartial(text, isPrompt = false)
                partialLine = null
            }
        }
    }

    private fun doAppendPartial(
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
        workQueue.submit {
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
    }

    override suspend fun appendLine(
        text: StyledString,
        ignoreWhenBlank: Boolean,
        showWhenClosed: String?,
    ) {
        workQueue.submit {
            mutex.withLock {
                // It's possible a component is added that is set prior
                // I don't think this happens in DR, so it's probably OK that we don't handle that case.
                partialLine = null
                doAppendLine(text, ignoreWhenBlank, showWhenClosed, isPrompt = false)
            }
        }
    }

    // Must be called from main thread
    private fun doAppendLine(
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
        workQueue.submit {
            mutex.withLock {
                // Images must be on their own line
                partialLine = null
                if (!showImages) return@withLock
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
    }

    override suspend fun updateComponent(
        name: String,
        value: StyledString,
    ) {
        workQueue.submit {
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
            applyStyling = applyStyling,
        )

    private fun playSound(line: String) {
        highlights.value.forEach { highlight ->
            val sound = highlight.sound
            if (sound != null && highlight.containsMatchIn(line)) {
                scope.launch {
                    soundPlayer.playSound(sound)
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

    override fun setApplyStyling(value: Boolean) {
        applyStyling = value
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
        applyStyling: Boolean,
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
            applyStyling = applyStyling,
        )
}

private val streamLineLogger = Logger.withTag("toStreamLine")

// Lines whose total render time exceeds this threshold get logged with a
// per-stage breakdown. Set to Duration.ZERO to log every line.
private val SLOW_LINE_THRESHOLD = 5.milliseconds

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
    applyStyling: Boolean,
): StreamTextLine {
    val t0 = TimeSource.Monotonic.markNow()
    val text =
        if (showTimestamp) {
            this + StyledString(" [${timestamp.toTimeString()}]")
        } else {
            this
        }
    val annotated =
        text.toAnnotatedString(
            variables = components,
            styleMap = presets,
            actionHandler = actionHandler,
        )
    val tAnnotated = TimeSource.Monotonic.markNow()
    val textWithComponents =
        if (applyStyling) {
            annotated
                .alter(alterations, streamName)
                ?.takeIf { !ignoreWhenBlank || it.isNotBlank() }
        } else {
            annotated.takeIf { !ignoreWhenBlank || it.text.isNotBlank() }
        }
    val tAltered = TimeSource.Monotonic.markNow()
    val textWithLinks =
        textWithComponents?.let { content ->
            if (applyStyling && markLinks) {
                buildAnnotatedString {
                    append(content)
                    markLinks(content, presets)
                }
            } else {
                content
            }
        }
    val tLinked = TimeSource.Monotonic.markNow()
    val highlightedResult =
        textWithLinks?.let { content ->
            if (applyStyling && content.text.isNotEmpty()) {
                content.highlight(highlights)
            } else {
                AnnotatedStringHighlightResult(content, emptyList())
            }
        }
    val tHighlighted = TimeSource.Monotonic.markNow()
    val lineStyle =
        flattenStyles(
            (highlightedResult?.entireLineStyles ?: emptyList()) +
                getEntireLineStyles(
                    variables = components,
                    styleMap = presets,
                ),
        )
    val result =
        StreamTextLine(
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
    val tEnd = TimeSource.Monotonic.markNow()
    val total = tEnd - t0
    if (total >= SLOW_LINE_THRESHOLD) {
        // Diagnostic: re-run annotate to distinguish structural vs. cold-path cost
        streamLineLogger.d {
            val annotate = tAnnotated - t0
            val alter = tAltered - tAnnotated
            val link = tLinked - tAltered
            val highlight = tHighlighted - tLinked
            val build = tEnd - tHighlighted
            val before = this.toString().take(200).replace("\n", "\\n")
            val after =
                result.text
                    ?.text
                    ?.take(200)
                    ?.replace("\n", "\\n") ?: "<filtered>"
            "Slow line in '$streamName' total=${total.inWholeMicroseconds}µs " +
                "annotate=${annotate.inWholeMicroseconds}µs " +
                "alter=${alter.inWholeMicroseconds}µs " +
                "link=${link.inWholeMicroseconds}µs " +
                "highlight=${highlight.inWholeMicroseconds}µs " +
                "build=${build.inWholeMicroseconds}µs " +
                "alterations=${alterations.size} highlights=${highlights.size} " +
                "before=\"$before\" after=\"$after\""
        }
    }
    return result
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
        val source = this@replaceWithRegex
        val length = source.length
        var pos = 0
        var match = pattern.find(source.text)
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > pos) {
                append(source.subSequence(pos, start))
            }
            append(buildReplacement(replacement, match))
            pos =
                if (end > pos) {
                    end
                } else {
                    // Zero-width or non-advancing match: skip one char to guarantee progress
                    if (pos < length) append(source.subSequence(pos, pos + 1))
                    pos + 1
                }
            if (pos > length) break
            match = pattern.find(source.text, pos)
        }
        if (pos < length) {
            append(source.subSequence(pos, length))
        }
    }

private fun buildReplacement(
    replacement: String,
    match: MatchResult,
): String {
    val sb = StringBuilder(replacement.length)
    var i = 0
    while (i < replacement.length) {
        val c = replacement[i]
        when {
            c == '\\' && i + 1 < replacement.length -> {
                sb.append(replacement[i + 1])
                i += 2
            }
            c == '$' && i + 1 < replacement.length -> {
                val next = replacement[i + 1]
                if (next.isDigit()) {
                    val n = next.digitToInt()
                    if (n < match.groups.size) {
                        match.groups[n]?.value?.let(sb::append)
                    }
                    i += 2
                } else if (next == '{') {
                    val close = replacement.indexOf('}', i + 2)
                    if (close >= 0) {
                        val name = replacement.substring(i + 2, close)
                        try {
                            (match.groups as? MatchNamedGroupCollection)?.get(name)?.value?.let(sb::append)
                        } catch (_: Exception) {
                            // Unknown group name — drop it, matching Java's behavior of throwing but be lenient here
                        }
                        i = close + 1
                    } else {
                        sb.append(c)
                        i++
                    }
                } else {
                    sb.append(c)
                    i++
                }
            }
            else -> {
                sb.append(c)
                i++
            }
        }
    }
    return sb.toString()
}
