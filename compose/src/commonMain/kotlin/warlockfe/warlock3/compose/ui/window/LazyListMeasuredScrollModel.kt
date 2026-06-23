package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * Tracks the measured pixel height of every line in a stream [androidx.compose.foundation.lazy.LazyColumn]
 * so a scrollbar can derive an exact content size and scroll offset instead of extrapolating from the
 * average height of the items that happen to be on screen.
 *
 * The stock lazy-list scrollbar adapter has no choice but to estimate: it only knows the items the
 * list has currently laid out, so it sets `contentSize = averageVisibleItemHeight * itemCount`. When
 * lines wrap to wildly different heights, that average shifts every time you drag into a taller or
 * shorter region, so the computed content size (and therefore the thumb height) jumps around. By
 * caching the real height of each line as it scrolls into view, regions that have already been seen
 * contribute their exact height; only never-yet-rendered lines fall back to the running average, and
 * the estimate converges as the user scrolls.
 *
 * Heights are keyed by [StreamLine.serialNumber] (stable across buffer trimming and re-filtering)
 * rather than by list index, which shifts whenever the scrollback buffer drops its oldest lines.
 *
 * @param state the backing list state.
 * @param itemCount current number of lines (must match the lazy list's item count).
 * @param keyAt stable serial number of the line at an index, or null if out of range.
 * @param isRendered whether the line at an index paints a non-zero-height row (see
 *   [rendersContent]); hidden/collapsed lines occupy a zero-height slot and must contribute 0.
 * @param measuredHeights cache of measured heights by serial number, populated by the caller as
 *   items are laid out. Recreate the map (rather than clearing it) whenever the line width or text
 *   style changes, since those invalidate every cached height.
 */
@Stable
class LazyListMeasuredScrollModel(
    private val state: LazyListState,
    private val itemCount: () -> Int,
    private val keyAt: (index: Int) -> Long?,
    private val isRendered: (index: Int) -> Boolean,
    private val measuredHeights: SnapshotStateMap<Long, Int>,
) {
    /** Mean of the known heights, used as the fallback for lines that have not been measured yet. */
    private fun averageHeight(): Double {
        var sum = 0L
        var count = 0
        for (height in measuredHeights.values) {
            if (height > 0) {
                sum += height
                count++
            }
        }
        return if (count == 0) 0.0 else sum.toDouble() / count
    }

    private fun heightOf(
        index: Int,
        average: Double,
    ): Double {
        if (!isRendered(index)) return 0.0
        val key = keyAt(index) ?: return average
        return measuredHeights[key]?.toDouble() ?: average
    }

    /** Total height of the content, summing measured heights and estimating the rest. */
    val contentSize: Double
        get() {
            val count = itemCount()
            val average = averageHeight()
            var total = 0.0
            for (i in 0 until count) {
                total += heightOf(i, average)
            }
            return total
        }

    val viewportSize: Double
        get() {
            val info = state.layoutInfo
            return (info.viewportEndOffset - info.viewportStartOffset).toDouble()
        }

    /** Distance from the top of the content to the top of the viewport. */
    val scrollOffset: Double
        get() {
            val first = state.firstVisibleItemIndex
            val average = averageHeight()
            var offset = 0.0
            for (i in 0 until first) {
                offset += heightOf(i, average)
            }
            return offset + state.firstVisibleItemScrollOffset
        }

    /** Jump so the content top sits [scrollOffset] pixels above the viewport top. */
    suspend fun scrollTo(scrollOffset: Double) {
        val count = itemCount()
        if (count == 0) return
        val target = scrollOffset.coerceAtLeast(0.0)
        val average = averageHeight()
        var consumed = 0.0
        for (i in 0 until count) {
            val height = heightOf(i, average)
            if (consumed + height > target) {
                state.scrollToItem(i, (target - consumed).toInt().coerceAtLeast(0))
                return
            }
            consumed += height
        }
        state.scrollToItem(count - 1)
    }
}
