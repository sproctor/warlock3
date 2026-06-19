package warlockfe.warlock3.compose.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import warlockfe.warlock3.compose.model.forMode
import warlockfe.warlock3.core.util.getIgnoringCase
import warlockfe.warlock3.core.util.toWarlockColor

/**
 * Resolved roundtime / casttime overlay colors for the command entry. Each timer has a [bar] color
 * for its segment bars and a [text] color for the countdown number.
 */
internal data class TimeBarColors(
    val roundTimeBar: Color,
    val roundTimeText: Color,
    val castTimeBar: Color,
    val castTimeText: Color,
)

// Fallback overlay colors, used when the active skin omits a `roundtime`/`casttime` color (and in
// previews, which provide no skin). Mirror the values in the bundled default skin's skin.json.
private val roundtimeBarOnDark = Color(0xFFC5483F)
private val roundtimeTextOnDark = Color(0xFFCF6259)
private val roundtimeOnLight = Color(0xFFE03C31)
private val castBarOnDark = Color(0xFF3F7CC5)
private val castTextOnDark = Color(0xFF5B9BD6)
private val castOnLight = Color(0xFF3030FF)

/**
 * The roundtime/casttime colors from the active skin's `roundtime` and `casttime` sections, resolved
 * for the [entryBackground]'s own luminance (not the app light/dark theme) so the overlay stays
 * legible over a custom command-entry background.
 */
@Composable
@ReadOnlyComposable
internal fun timeBarColors(entryBackground: Color): TimeBarColors {
    val skin = LocalSkin.current
    val isDark = entryBackground.luminance() < CONTRAST_CROSSOVER_LUMINANCE
    val roundtime = skin.getIgnoringCase("roundtime")
    val casttime = skin.getIgnoringCase("casttime")
    return TimeBarColors(
        roundTimeBar =
            roundtime?.bar.forMode(isDark)?.toWarlockColor().toColor(
                default = if (isDark) roundtimeBarOnDark else roundtimeOnLight,
            ),
        roundTimeText =
            roundtime?.color.forMode(isDark)?.toWarlockColor().toColor(
                default = if (isDark) roundtimeTextOnDark else roundtimeOnLight,
            ),
        castTimeBar =
            casttime?.bar.forMode(isDark)?.toWarlockColor().toColor(
                default = if (isDark) castBarOnDark else castOnLight,
            ),
        castTimeText =
            casttime?.color.forMode(isDark)?.toWarlockColor().toColor(
                default = if (isDark) castTextOnDark else castOnLight,
            ),
    )
}
