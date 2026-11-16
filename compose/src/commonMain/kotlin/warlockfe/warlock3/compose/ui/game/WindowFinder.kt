package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import warlockfe.warlock3.compose.ui.window.ComposeTextStream
import warlockfe.warlock3.compose.ui.window.StreamTextLine
import warlockfe.warlock3.compose.ui.window.WindowFindController
import warlockfe.warlock3.compose.ui.window.WindowFindUiState

/**
 * Find-in-window ("Ctrl+F"). [state] is non-null while the find overlay is open, and names the
 * window find was opened over; the overlay renders only there.
 *
 * Matches are ordered newest-first (the bottom-most occurrence is index 0), so stepping "next"
 * moves further up the buffer.
 */
internal class WindowFinder(
    private val streamFor: (windowName: String) -> ComposeTextStream?,
) : WindowFindController {
    private val stateFlow = MutableStateFlow<WindowFindUiState?>(null)
    private val focusedFlow = MutableStateFlow(false)
    private var matches: List<FindMatch> = emptyList()
    private var index = 0
    private var query = ""
    private var windowName = MAIN_WINDOW_NAME

    override val state: StateFlow<WindowFindUiState?> = stateFlow.asStateFlow()

    override val focused: StateFlow<Boolean> = focusedFlow.asStateFlow()

    /** Steps to the next match, or opens find over [selectedWindow] when it is closed. */
    fun nextOrOpen(selectedWindow: String) {
        if (stateFlow.value == null) open(selectedWindow) else step(1)
    }

    /** Steps to the previous match, or opens find over [selectedWindow] when it is closed. */
    fun previousOrOpen(selectedWindow: String) {
        if (stateFlow.value == null) open(selectedWindow) else step(-1)
    }

    override fun setQuery(query: String) {
        if (stateFlow.value == null) return
        this.query = query
        index = 0
        recomputeMatches()
    }

    override fun next() = step(1)

    override fun previous() = step(-1)

    override fun close() {
        stateFlow.value = null
        focusedFlow.value = false
        matches = emptyList()
        index = 0
        query = ""
    }

    override fun setFocused(focused: Boolean) {
        focusedFlow.value = focused
    }

    // Open the overlay over [target]. No-op if that window has no text stream (a panel window).
    // Starts with an empty query, so nothing matches until the user types.
    private fun open(target: String) {
        if (streamFor(target) == null) return
        windowName = target
        query = ""
        index = 0
        recomputeMatches()
    }

    // Step to another match, wrapping around. +1 is "next" (further up/older), -1 is "previous".
    private fun step(delta: Int) {
        if (stateFlow.value == null) return
        val size = matches.size
        if (size == 0) return
        index = ((index + delta) % size + size) % size
        publish()
    }

    // Re-scan the target window's lines for the current query, ordering matches newest-first so the
    // bottom-most occurrence is selected first.
    private fun recomputeMatches() {
        val stream = streamFor(windowName)
        val docOrder = mutableListOf<FindMatch>()
        if (query.isNotEmpty() && stream != null) {
            stream.lines.value.forEach { line ->
                val text = (line as? StreamTextLine)?.text?.text
                if (text != null) {
                    var i = text.indexOf(query, ignoreCase = true)
                    while (i >= 0) {
                        docOrder.add(FindMatch(line.serialNumber, i until (i + query.length)))
                        i = text.indexOf(query, startIndex = i + query.length, ignoreCase = true)
                    }
                }
            }
        }
        matches = docOrder.asReversed()
        index = index.coerceIn(0, maxOf(0, matches.size - 1))
        publish()
    }

    private fun publish() {
        val current = matches.getOrNull(index)
        stateFlow.value =
            WindowFindUiState(
                windowName = windowName,
                query = query,
                totalMatches = matches.size,
                currentNumber = if (matches.isEmpty()) 0 else index + 1,
                matchRangesBySerial =
                    matches
                        .groupBy { it.serialNumber }
                        .mapValues { (_, matches) -> matches.map { it.range }.sortedBy { it.first } },
                currentSerial = current?.serialNumber,
                currentRange = current?.range,
            )
    }
}

/** A single find-in-window match: the line's serial number and the matched character range in it. */
private data class FindMatch(
    val serialNumber: Long,
    val range: IntRange,
)
