package warlockfe.warlock3.compose.ui.window

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.model.LiteralHighlight
import warlockfe.warlock3.compose.model.RegexHighlight
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.model.wordTokensOf
import warlockfe.warlock3.compose.util.AnnotatedStringHighlightResult
import warlockfe.warlock3.compose.util.HighlightIndex
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
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
    private var suppressPrompts: Boolean,
    private val highlights: StateFlow<HighlightIndex>,
    private val names: StateFlow<List<ViewHighlight>>,
    private val alterations: StateFlow<List<CompiledAlteration>>,
    private val presets: StateFlow<Map<String, StyleDefinition>>,
    private val soundPlayer: SoundPlayer,
    private val workQueue: StreamWorkQueue,
    private val scope: CoroutineScope,
) : TextStream,
    StreamWorkQueue.Flushable {
    // ArrayDeque so trimming the oldest line (removeAt(0)) once the buffer fills is O(1) rather than
    // the O(n) front shift of an ArrayList.
    private val cacheLines = ArrayDeque<CachedLine?>(maxLines)
    private val finishedLines = ArrayDeque<StreamLine>(maxLines)

    // Seeded with an (empty) OffsetList rather than emptyList() so every value this flow ever holds
    // shares OffsetList's referential equality. emptyList() compares by content, which would make the
    // first published OffsetList equal to it (both empty) and let StateFlow swallow that emission.
    val lines = MutableStateFlow<List<StreamLine>>(OffsetList(persistentListOf(), 0))

    // The displayed lines are an append-only persistent list plus a logical start index. Once the
    // buffer fills, evicting the oldest displayed line (which happens on every newline) is then an
    // O(1) start bump rather than an O(n) front removal, while appends stay O(log n). The backing is
    // compacted once the dropped prefix grows large, amortizing that to O(1) per line.
    private var displayBacking: PersistentList<StreamLine> = persistentListOf()
    private var displayStart: Int = 0
    private val components = mutableMapOf<String, StyledString>()

    private var nextSerialNumber = 0L
    private var removedLines = 0L

    // Set when the displayed lines changed but the new snapshot has not been emitted yet; the work
    // queue coalesces this into a single publish per drain batch (see publishLines/flush).
    private var pendingPublish = false

    private var applyStyling: Boolean = true

    // When true, only lines that match a name in the names list are shown
    private var nameFilter: Boolean = false

    private var partialLine: StyledString? = null

    private val componentLocations = mutableMapOf<String, Set<Long>>()

    var actionHandler: ((WarlockAction) -> Unit)? = null

    init {
        // Re-filter displayed lines whenever the names list changes
        names
            .onEach {
                if (nameFilter) {
                    workQueue.submit("namefilter") {
                        linesUpdated()
                    }
                }
            }.launchIn(scope)

        // Re-render the buffer whenever a styling input (presets/fonts, highlights, or alterations)
        // changes. The cached lines bake all three into their AnnotatedStrings at append time, so
        // without this they keep the old styling until each line is otherwise rebuilt. These change
        // rarely (the user edits settings), so a full re-render of the buffer is acceptable. drop(1)
        // skips each flow's replayed current value (nothing has been appended yet at construction, so
        // re-rendering then would be a no-op).
        merge(
            presets.drop(1).map { },
            highlights.drop(1).map { },
            alterations.drop(1).map { },
        ).onEach {
            workQueue.submit("rerender") {
                rerenderAllLines()
            }
        }.launchIn(scope)
    }

    override suspend fun appendPartial(
        text: StyledString,
        isPrompt: Boolean,
    ) {
        workQueue.submit("partial") {
            doAppendPartial(text, isPrompt)
        }
    }

    override suspend fun appendPartialAndEol(text: StyledString) {
        workQueue.submit("partial") {
            doAppendPartial(text, isPrompt = false)
            partialLine = null
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
            val newLine = cachedLineToStreamLine(cachedLine, serialNumber)
            finishedLines[finishedLines.lastIndex] = newLine
            applyViewChange(replaceLineInView(serialNumber, newLine))
        }
    }

    override suspend fun clear() {
        workQueue.submit("clear") {
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
        workQueue.submit("append") {
            // It's possible a component is added that is set prior
            // I don't think this happens in DR, so it's probably OK that we don't handle that case.
            partialLine = null
            doAppendLine(text, ignoreWhenBlank, showWhenClosed, isPrompt = false)
        }
    }

    // Must be called from main thread
    private fun doAppendLine(
        text: StyledString,
        ignoreWhenBlank: Boolean,
        showWhenClosed: String?,
        isPrompt: Boolean,
    ) {
        val serialNumber = nextSerialNumber++
        addComponentLocations(text, serialNumber)
        val cachedLine = styledStringToCachedLine(text, ignoreWhenBlank, showWhenClosed, isPrompt)
        cacheLines.add(cachedLine)
        val line = cachedLineToStreamLine(cachedLine, serialNumber)
        finishedLines.add(line)
        removeLines()
        line.text?.let { playSound(it.text) }
        appendLineToView(line)
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

    // Trim the buffer down to the cap. Callers append first, then trim, so this evicts the oldest
    // lines until at most maxLines remain (maxLines <= 0 means unbounded).
    private fun removeLines() {
        while (maxLines > 0 && finishedLines.size > maxLines) {
            finishedLines.removeAt(0)
            cacheLines.removeAt(0)
            removedLines++
            // Intentionally leak components here. They don't exist in the main window,
            // and no other windows get long enough
        }
    }

    override suspend fun appendResource(url: String) {
        workQueue.submit("resource") {
            // Images must be on their own line
            partialLine = null
            if (!showImages) return@submit
            cacheLines.add(null)
            val line =
                StreamImageLine(
                    url = url,
                    serialNumber = nextSerialNumber++,
                )
            finishedLines.add(line)
            removeLines()
            appendLineToView(line)
        }
    }

    override suspend fun updateComponent(
        name: String,
        value: StyledString,
    ) {
        workQueue.submit("component") {
            updateComponentSync(name, value)
        }
    }

    // The body of a component update, runnable directly on the work-queue consumer. WindowRegistry
    // batches one server component update into a single queue op that calls this on every stream,
    // instead of each stream enqueuing its own op (which multiplied queue traffic by the open-window
    // count for every component change). Must only be called from the work queue.
    fun updateComponentSync(
        name: String,
        value: StyledString,
    ) {
        components[name] = value
        // A component can appear on many lines; re-render them all, then publish once instead
        // of emitting a fresh snapshot per affected line.
        var change = ViewChange.NONE
        componentLocations[name]?.forEach { serialNumber ->
            val lineNumber = (serialNumber - removedLines).toInt()
            // If the component has scrolled back past the buffer, ignore it
            if (lineNumber >= 0) {
                change = change.coalesce(updateLine(lineNumber))
            }
        }
        applyViewChange(change)
    }

    // Re-render every buffered line from its cached source against the current presets, highlights,
    // and alterations, then rebuild the displayed list. Called when one of those styling inputs
    // changes so already displayed lines pick up the new fonts/styles/replacements. Image lines have
    // no cached source (null) and are kept as-is. cacheLines and finishedLines are index-parallel, so
    // a serial-preserving in-place rebuild is enough.
    private fun rerenderAllLines() {
        for (i in finishedLines.indices) {
            val cachedLine = cacheLines[i] ?: continue
            val serialNumber = finishedLines[i].serialNumber
            finishedLines[i] = cachedLineToStreamLine(cachedLine, serialNumber)
        }
        linesUpdated()
    }

    // Re-render a single buffered line in place and stage (without publishing) the resulting change.
    private fun updateLine(lineNumber: Int): ViewChange {
        val cachedLine = cacheLines[lineNumber] ?: return ViewChange.NONE
        val serialNumber = finishedLines[lineNumber].serialNumber
        val newLine = cachedLineToStreamLine(cachedLine, serialNumber)
        finishedLines[lineNumber] = newLine
        return replaceLineInView(serialNumber, newLine)
    }

    // Append a single line to the displayed list instead of rebuilding and re-filtering all of it.
    private fun appendLineToView(line: StreamLine) {
        var changed = false
        // removeLines() evicted the oldest lines from the buffer; being the smallest serial numbers
        // they sit at the front of the serial-ordered view (when shown). Advancing the logical start
        // drops them in O(1) each instead of an O(n) front removal.
        val oldestSerial = finishedLines.firstOrNull()?.serialNumber
        if (oldestSerial != null) {
            while (displayStart < displayBacking.size &&
                displayBacking[displayStart].serialNumber < oldestSerial
            ) {
                displayStart++
                changed = true
            }
        }
        if (linePassesFilter(line)) {
            displayBacking = displayBacking.adding(line)
            changed = true
        }
        if (changed) {
            compactBacking()
            publishLines()
        }
    }

    // Replace a single already-displayed line, returning the view change to apply (the caller
    // publishes, so a batch of replacements can emit one snapshot). Updates almost always target the
    // most recent line, so check the tail before binary searching the live window; PersistentList.set
    // shares structure, avoiding a full copy. A line's prompt flag is fixed, but its name-filter match
    // can change, so handle it appearing/disappearing.
    private fun replaceLineInView(
        serialNumber: Long,
        newLine: StreamLine,
    ): ViewChange {
        val lastSerial = displayBacking.lastOrNull()?.serialNumber
        val backingIndex =
            when {
                lastSerial == serialNumber -> {
                    displayBacking.lastIndex
                }

                // A serial newer than the last displayed line cannot be in the live window (e.g. a
                // suppressed/filtered partial line being streamed), so skip the binary search.
                lastSerial == null || serialNumber > lastSerial -> {
                    -1
                }

                else -> {
                    displayBacking.binarySearch(displayStart, displayBacking.size) {
                        it.serialNumber.compareTo(serialNumber)
                    }
                }
            }
        val shouldShow = linePassesFilter(newLine)
        return when {
            backingIndex >= displayStart && shouldShow -> {
                displayBacking = displayBacking.replacingAt(backingIndex, newLine)
                ViewChange.PUBLISH
            }

            backingIndex >= displayStart -> {
                // Was visible, now filtered out.
                displayBacking = displayBacking.removingAt(backingIndex)
                compactBacking()
                ViewChange.PUBLISH
            }

            shouldShow -> {
                // Was filtered out, now visible: rebuild so it lands in serial order.
                ViewChange.REBUILD
            }

            // Filtered out before and after - nothing to update.
            else -> {
                ViewChange.NONE
            }
        }
    }

    private fun applyViewChange(change: ViewChange) {
        when (change) {
            ViewChange.PUBLISH -> {
                publishLines()
            }

            ViewChange.REBUILD -> {
                linesUpdated()
            }

            ViewChange.NONE -> {}
        }
    }

    // The net effect of staging one or more line replacements. REBUILD subsumes PUBLISH subsumes
    // NONE, so a batch escalates to the strongest change any one line produced (a rebuild reads the
    // already-updated finishedLines, so it covers the set/remove mutations too).
    private enum class ViewChange {
        NONE,
        PUBLISH,
        REBUILD,
        ;

        fun coalesce(other: ViewChange): ViewChange = if (other.ordinal > ordinal) other else this
    }

    // Drop the evicted prefix once it is as large as the live portion. The rebuild is O(n) but runs
    // about once per n evictions, so it amortizes to O(1) per newline while keeping the backing from
    // growing without bound.
    private fun compactBacking() {
        if (displayStart > 0 && displayStart >= displayBacking.size - displayStart) {
            displayBacking = displayBacking.subList(displayStart, displayBacking.size).toPersistentList()
            displayStart = 0
        }
    }

    private fun publishLines() {
        // Coalesce: mark the displayed lines dirty and let the work queue publish once the current
        // drain batch finishes, instead of emitting a fresh snapshot (and waking the UI) per op.
        pendingPublish = true
        workQueue.scheduleFlush(this)
    }

    // Emit the latest displayed lines. Run by the work queue after a drain batch, on the consumer.
    override fun flush() {
        if (!pendingPublish) return
        pendingPublish = false
        lines.value = OffsetList(displayBacking, displayStart)
    }

    // An O(1), immutable view over [backing] starting at [start], so the displayed window can be
    // published without copying while the backing still carries an evicted prefix.
    //
    // Equality is referential, not a List's usual content equality: publishLines() emits a fresh
    // instance only when the displayed lines actually changed, so identity inequality already means
    // "changed". That lets StateFlow's de-dup and Compose's snapshot state skip an O(n) content
    // comparison on every update - which otherwise dominates same-window updates such as a streaming
    // partial line or a component refresh.
    private class OffsetList(
        private val backing: List<StreamLine>,
        private val start: Int,
    ) : AbstractList<StreamLine>() {
        override val size: Int get() = backing.size - start

        override fun get(index: Int): StreamLine = backing[start + index]

        override fun equals(other: Any?): Boolean = this === other

        // Kept O(1) and consistent with the identity equals above (equal instances are the same
        // instance, so they trivially share a hash) rather than AbstractList's O(n) content hash. This
        // list is never used as a hash key; the override only exists to avoid that content hash.
        override fun hashCode(): Int = size
    }

    private fun cachedLineToStreamLine(
        cachedLine: CachedLine,
        serialNumber: Long,
    ): StreamTextLine =
        cachedLine.toStreamLine(
            streamName = id,
            showTimestamp = showTimestamps,
            serialNumber = serialNumber,
            highlightIndex = highlights.value,
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
        highlights.value.highlights.forEach { highlight ->
            val sound = highlight.sound
            if (sound != null && highlight.containsMatchIn(line)) {
                scope.launch {
                    soundPlayer.playSound(sound)
                }
            }
        }
    }

    suspend fun setMaxLines(maxLines: Int) {
        workQueue.submit("maxlines") {
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

    fun setSuppressPrompts(suppressPrompts: Boolean) {
        if (this.suppressPrompts == suppressPrompts) return
        this.suppressPrompts = suppressPrompts
        scheduleRelayout()
    }

    // Re-run the filter and rebuild the displayed list on the work queue. Callable from any thread.
    private fun scheduleRelayout() {
        scope.launch {
            workQueue.submit("relayout") {
                linesUpdated()
            }
        }
    }

    private fun linesUpdated() {
        displayBacking = finishedLines.filter { linePassesFilter(it) }.toPersistentList()
        displayStart = 0
        publishLines()
    }

    private fun linePassesFilter(line: StreamLine): Boolean =
        (!nameFilter || lineMatchesName(line)) &&
            (!suppressPrompts || (line as? StreamTextLine)?.isPrompt != true)

    private fun lineMatchesName(line: StreamLine): Boolean {
        val text = (line as? StreamTextLine)?.text?.text ?: return false
        if (text.isEmpty()) return false
        return names.value.any { it.containsMatchIn(text) }
    }

    override fun showTimestamps(value: Boolean) {
        showTimestamps = value
    }

    override fun setApplyStyling(value: Boolean) {
        applyStyling = value
    }

    override fun setNameFilter(value: Boolean) {
        if (nameFilter == value) return
        nameFilter = value
        scheduleRelayout()
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
        highlightIndex: HighlightIndex,
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
            highlightIndex = highlightIndex,
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

// Lines whose total render time exceeds this threshold get logged with a per-stage breakdown.
// The frame budget at 60fps is ~16ms for all lines combined, so a single line over 1ms is already
// consuming a significant fraction of a frame. Set to Duration.ZERO to log every line.
private val SLOW_LINE_THRESHOLD = 500.microseconds

fun StyledString.toStreamLine(
    streamName: String,
    showTimestamp: Boolean,
    ignoreWhenBlank: Boolean,
    serialNumber: Long,
    showWhenClosed: String?,
    isPrompt: Boolean,
    timestamp: Instant,
    highlightIndex: HighlightIndex,
    alterations: List<CompiledAlteration>,
    presets: Map<String, StyleDefinition>,
    components: Map<String, StyledString>,
    actionHandler: (WarlockAction) -> Unit,
    markLinks: Boolean,
    applyStyling: Boolean,
): StreamTextLine {
    val source = this

    // Runs the full per-line transform once and returns the line plus a per-stage timing breakdown.
    fun render(): RenderedLine {
        val t0 = TimeSource.Monotonic.markNow()
        val text =
            if (showTimestamp) {
                source + StyledString(" [${timestamp.toTimeString()}]")
            } else {
                source
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
                    content.highlight(highlightIndex)
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
        val line =
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
        return RenderedLine(
            line = line,
            total = tEnd - t0,
            annotate = tAnnotated - t0,
            alter = tAltered - tAnnotated,
            link = tLinked - tAltered,
            highlight = tHighlighted - tLinked,
            build = tEnd - tHighlighted,
        )
    }

    val rendered = render()
    if (rendered.total >= SLOW_LINE_THRESHOLD) {
        // The first pass can be inflated by a one-off GC/JIT pause that has nothing to do with this
        // line's real cost, so re-run it and only warn if it is reproducibly slow. The retry breakdown
        // reflects steady-state cost; the first-pass total is reported for reference.
        val retry = render()
        if (retry.total >= SLOW_LINE_THRESHOLD) {
            logSlowLine(
                streamName = streamName,
                serialNumber = serialNumber,
                source = source,
                first = rendered,
                steady = retry,
                alterations = alterations,
                highlightIndex = highlightIndex,
            )
        }
    }
    return rendered.line
}

// Warn about a reproducibly slow line with a per-stage timing breakdown and the inputs that drive the
// cost. [first] is the (possibly inflated) first pass; [steady] is the re-run whose per-stage numbers
// reflect steady-state cost.
private fun logSlowLine(
    streamName: String,
    serialNumber: Long,
    source: StyledString,
    first: RenderedLine,
    steady: RenderedLine,
    alterations: List<CompiledAlteration>,
    highlightIndex: HighlightIndex,
) {
    val regexHighlights = highlightIndex.highlights.filterIsInstance<RegexHighlight>()
    val literalHighlights = highlightIndex.highlights.filterIsInstance<LiteralHighlight>()
    val wholeWordLiterals = literalHighlights.count { !it.matchPartialWord }
    val partialWordLiterals = literalHighlights.count { it.matchPartialWord }
    // How many of the highlights the index actually checked this line (the rest were excluded by
    // probe token). Separates real highlight-matching cost from the total, which on word-rich lines
    // pulls more candidates than a short line does.
    val candidates = highlightIndex.candidateCount(wordTokensOf(first.line.text?.text ?: ""))
    streamLineLogger.w {
        val before = source.toText().take(200).replace("\n", "\\n")
        val after =
            first.line.text
                ?.text
                ?.take(200)
                ?.replace("\n", "\\n") ?: "<filtered>"
        "Slow line in '$streamName' total=${steady.total.inWholeMicroseconds}µs " +
            "(first=${first.total.inWholeMicroseconds}µs) " +
            "serial=$serialNumber " +
            "annotate=${steady.annotate.inWholeMicroseconds}µs " +
            "alter=${steady.alter.inWholeMicroseconds}µs " +
            "link=${steady.link.inWholeMicroseconds}µs " +
            "highlight=${steady.highlight.inWholeMicroseconds}µs " +
            "build=${steady.build.inWholeMicroseconds}µs " +
            "alterations=${alterations.size} highlights=${highlightIndex.highlights.size} " +
            "(regex=${regexHighlights.size} " +
            "literalWhole=$wholeWordLiterals literalPartial=$partialWordLiterals) " +
            "candidates=$candidates " +
            "before=\"$before\" after=\"$after\""
    }
}

// A rendered stream line plus the per-stage timing of the transform that produced it.
private class RenderedLine(
    val line: StreamTextLine,
    val total: Duration,
    val annotate: Duration,
    val alter: Duration,
    val link: Duration,
    val highlight: Duration,
    val build: Duration,
)

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
        when (val c = replacement[i]) {
            '\\' if i + 1 < replacement.length -> {
                sb.append(replacement[i + 1])
                i += 2
            }

            '$' if i + 1 < replacement.length -> {
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
