package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Snapshot of an in-progress find-in-window session, rendered as a floating overlay over the target
 * window. [matchRangesBySerial] maps a line's serial number to the character ranges to highlight in
 * that line; [currentSerial]/[currentRange] mark the one match that is "selected" (emphasized and
 * scrolled into view). [currentNumber] is 1-based (0 when there are no matches).
 */
data class WindowFindUiState(
    val windowName: String,
    val query: String,
    val totalMatches: Int,
    val currentNumber: Int,
    val matchRangesBySerial: Map<Long, List<IntRange>>,
    val currentSerial: Long?,
    val currentRange: IntRange?,
)

/**
 * Drives the find overlay. Provided high in each platform game view via [LocalWindowFindController]
 * so the deeply-nested window content can read the session state and forward overlay actions without
 * threading callbacks through every window layer. [state] is null while find is closed.
 */
interface WindowFindController {
    val state: StateFlow<WindowFindUiState?>

    /** True while the find bar itself holds focus; the game view's key handler steps aside then. */
    val focused: StateFlow<Boolean>

    fun setQuery(query: String)

    /** Move to the next match (further up / older in the buffer). */
    fun next()

    /** Move to the previous match (down / newer in the buffer). */
    fun previous()

    fun close()

    fun setFocused(focused: Boolean)
}

private object NoOpWindowFindController : WindowFindController {
    override val state = MutableStateFlow<WindowFindUiState?>(null)

    override val focused = MutableStateFlow(false)

    override fun setQuery(query: String) {}

    override fun next() {}

    override fun previous() {}

    override fun close() {}

    override fun setFocused(focused: Boolean) {}
}

val LocalWindowFindController = compositionLocalOf<WindowFindController> { NoOpWindowFindController }

// Browser-style find colors: all matches amber, the current match a stronger orange, both with black
// text so they stay readable over any window text color.
private val findMatchSpan = SpanStyle(background = Color(0xFFFFE082), color = Color.Black)
private val findCurrentMatchSpan = SpanStyle(background = Color(0xFFFFB300), color = Color.Black)

/** Returns a copy of this line with find-match backgrounds layered on top of the existing styling. */
fun AnnotatedString.withFindHighlight(
    ranges: List<IntRange>,
    currentRange: IntRange?,
): AnnotatedString {
    if (ranges.isEmpty()) return this
    val builder = AnnotatedString.Builder(this)
    ranges.forEach { range ->
        val span = if (range == currentRange) findCurrentMatchSpan else findMatchSpan
        builder.addStyle(span, range.first, range.last + 1)
    }
    return builder.toAnnotatedString()
}
