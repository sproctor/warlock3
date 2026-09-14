package warlockfe.warlock3.core.window

import warlockfe.warlock3.core.text.WarlockColor
import kotlin.time.Duration

/**
 * A request to fade a window's background to [color] and back to its own over [duration]. The
 * [serial] tells one request from the next, so a window asked for the same flash twice fades
 * twice.
 */
data class BackgroundFlash(
    val color: WarlockColor,
    val duration: Duration,
    val serial: Long,
)
