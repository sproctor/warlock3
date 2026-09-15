package warlockfe.warlock3.core.window

import warlockfe.warlock3.core.text.WarlockColor
import kotlin.time.Duration

/**
 * A request to show a window's background as [color] for [total], fading to it over the first
 * [fadeIn] of that and back to the window's own over the last [fadeOut]; a fade of zero is a
 * cut. The [serial] tells one request from the next, so a window asked for the same flash twice
 * shows it twice.
 */
data class BackgroundFlash(
    val color: WarlockColor,
    val total: Duration,
    val fadeIn: Duration,
    val fadeOut: Duration,
    val serial: Long,
) {
    /** How long the colour is held between the fades. */
    val hold: Duration get() = total - fadeIn - fadeOut
}
