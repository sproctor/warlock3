package warlockfe.warlock3.compose.ui.window

import io.github.oikvpqya.compose.fastscroller.ScrollbarAdapter

/**
 * Adapts the platform-agnostic [LazyListMeasuredScrollModel] to fastscroller's [ScrollbarAdapter]
 * contract. The model supplies an exact content size built from per-line measured heights, so the
 * drag handle keeps a stable length while scrolling through stream lines of varying height instead
 * of the average-based estimate used by `rememberScrollbarAdapter`. This is the mobile counterpart
 * to the desktop adapter, which implements the native `androidx.compose.foundation.v2` interface.
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
