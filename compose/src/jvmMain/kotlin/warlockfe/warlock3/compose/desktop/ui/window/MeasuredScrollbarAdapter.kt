package warlockfe.warlock3.compose.desktop.ui.window

import androidx.compose.foundation.v2.ScrollbarAdapter
import warlockfe.warlock3.compose.ui.window.LazyListMeasuredScrollModel

/**
 * Adapts the platform-agnostic [LazyListMeasuredScrollModel] to the desktop scrollbar's
 * [ScrollbarAdapter] contract. The model supplies an exact content size built from per-line measured
 * heights, so the scrollbar thumb keeps a stable size while dragging through stream lines of varying
 * height (instead of the average-based estimate used by the stock lazy-list adapter).
 */
internal class MeasuredScrollbarAdapter(
    private val model: LazyListMeasuredScrollModel,
) : ScrollbarAdapter {
    override val scrollOffset: Double
        get() = model.scrollOffset

    override val contentSize: Double
        get() = model.contentSize

    override val viewportSize: Double
        get() = model.viewportSize

    override suspend fun scrollTo(scrollOffset: Double) = model.scrollTo(scrollOffset)
}
