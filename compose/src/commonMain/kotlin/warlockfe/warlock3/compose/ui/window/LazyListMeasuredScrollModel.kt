package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlin.math.abs

/**
 * Tracks the measured pixel height of every line in a stream [androidx.compose.foundation.lazy.LazyColumn]
 * so a scrollbar can derive an exact content size and scroll offset instead of extrapolating from the
 * average height of the items that happen to be on screen.
 *
 * The stock lazy-list scrollbar adapter has no choice but to estimate: it only knows the items the
 * list has currently laid out, so it sets `contentSize = averageVisibleItemHeight * itemCount`. When
 * lines wrap to wildly different heights, that average shifts every time you drag into a taller or
 * shorter region, so the computed content size (and therefore the thumb height) jumps around. By
 * caching the real height of each line as it scrolls into view - and measuring off-screen lines via
 * [renderedHeight] - regions contribute their exact height; only a line that has not been measured by
 * either path yet falls back to the running average, which is a brief transient as the caller's
 * measuring pass catches up.
 *
 * Heights are keyed by [StreamLine.serialNumber] (stable across buffer trimming and re-filtering)
 * rather than by list index, which shifts whenever the scrollback buffer drops its oldest lines.
 *
 * The geometry is derived from a [prefixSums] snapshot that is rebuilt only when a height, a line's
 * visibility, or the line set changes - not on every scroll - so the per-frame `contentSize` /
 * `scrollOffset` reads the scrollbar makes while dragging are O(1) instead of re-summing the buffer.
 *
 * @param state the backing list state.
 * @param itemCount current number of lines (must match the lazy list's item count).
 * @param keyAt stable serial number of the line at an index, or null if out of range.
 * @param isRendered whether the line at an index paints a non-zero-height row (see
 *   [rendersContent]); hidden/collapsed lines occupy a zero-height slot and must contribute 0.
 * @param measuredHeights cache of measured heights by serial number, populated by the caller as
 *   items are laid out (and pre-filled for off-screen lines). Recreate the map (rather than clearing
 *   it) whenever the line width or text style changes, since those invalidate every cached height.
 */
@Stable
class LazyListMeasuredScrollModel(
    private val state: LazyListState,
    private val itemCount: () -> Int,
    private val keyAt: (index: Int) -> Long?,
    private val isRendered: (index: Int) -> Boolean,
    private val measuredHeights: SnapshotStateMap<Long, Int>,
) {
    /** Mean of the known heights, the fallback for any line not yet measured. */
    private val averageHeight =
        derivedStateOf {
            var sum = 0L
            var count = 0
            for (height in measuredHeights.values) {
                if (height > 0) {
                    sum += height
                    count++
                }
            }
            if (count == 0) 0.0 else sum.toDouble() / count
        }

    /**
     * Running prefix sums of the line heights: `prefixSums[i]` is the summed height of lines
     * `0 until i`, so `prefixSums[itemCount]` is the whole content (excluding the list's content
     * padding). Backed by [derivedStateOf] so it rebuilds only when a height, a line's visibility, or
     * the line set changes - never on a plain scroll - which keeps `contentSize` and `scrollOffset`
     * O(1) per read.
     */
    private val prefixSums =
        derivedStateOf {
            val count = itemCount()
            val average = averageHeight.value
            val sums = DoubleArray(count + 1)
            var total = 0.0
            for (i in 0 until count) {
                sums[i] = total
                total += heightOf(i, average)
            }
            sums[count] = total
            sums
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
            val sums = prefixSums.value
            // Content padding is read live here (cheap, and changes only on a relayout) rather than
            // inside the prefix-sum derivation, so that scrolling does not invalidate the sums. This
            // matches the stock adapter, which adds beforeContentPadding + afterContentPadding so the
            // scrollable range (contentSize - viewportSize) reaches the list's true bottom.
            val info = state.layoutInfo
            return sums[sums.size - 1] + info.beforeContentPadding + info.afterContentPadding
        }

    val viewportSize: Double
        get() {
            val info = state.layoutInfo
            return (info.viewportEndOffset - info.viewportStartOffset).toDouble()
        }

    /** Distance from the top of the content to the top of the viewport. */
    val scrollOffset: Double
        get() {
            val sums = prefixSums.value
            val first = state.firstVisibleItemIndex.coerceIn(0, sums.size - 1)
            return sums[first] + state.firstVisibleItemScrollOffset
        }

    /** Jump so the content top sits [scrollOffset] pixels above the viewport top. */
    suspend fun scrollTo(scrollOffset: Double) {
        // For moves within a viewport, scroll incrementally through the list's own scrollBy instead of
        // snapping to a computed item. snapToItemIndex (used by scrollToItem) bypasses ScrollScope's
        // scrollBy, so it never sets the list's lastScrolledBackward/Forward flags that the sticky
        // auto-scroll keys on; a thumb drag would then leave "sticky" engaged and let the next incoming
        // line yank the view back to the bottom. scrollBy also keeps the motion smooth across lines of
        // differing height. Snap only for jumps larger than a viewport (track clicks, dragging across
        // the whole track), where an incremental scroll would be needlessly slow. This mirrors the
        // stock lazy-list scrollbar adapter.
        val distance = scrollOffset - this.scrollOffset
        if (abs(distance) <= viewportSize) {
            state.scrollBy(distance.toFloat())
        } else {
            snapTo(scrollOffset)
        }
    }

    private suspend fun snapTo(scrollOffset: Double) {
        val sums = prefixSums.value
        val count = sums.size - 1
        if (count <= 0) return
        val target = scrollOffset.coerceAtLeast(0.0)
        for (i in 0 until count) {
            if (sums[i + 1] > target) {
                state.scrollToItem(i, (target - sums[i]).toInt().coerceAtLeast(0))
                return
            }
        }
        state.scrollToItem(count - 1)
    }
}
