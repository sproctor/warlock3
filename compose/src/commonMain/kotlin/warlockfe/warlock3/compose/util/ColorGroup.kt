package warlockfe.warlock3.compose.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.model.forMode
import warlockfe.warlock3.core.util.getIgnoringCase
import warlockfe.warlock3.core.util.toWarlockColor

internal data class ColorGroup(
    val text: Color,
    val bar: Color,
    val background: Color,
)

// Fallback progress-bar colors, used when the active skin omits a `progressBar` section (and in
// previews, which provide no skin). Mirror the values in the bundled default skin's skin.json.
// Slate rather than a vital's hue: a bar that reaches these has no skin entry of its own, so it is
// deliberately unclassified and should not read as health, mana, stamina, spirit or concentration.
private val barOnLight = Color(0xFF546E8C)
private val barOnDark = Color(0xFF7C97B6)
private val trackOnLight = Color(0xFFC6C6BE)
private val trackOnDark = Color(0xFF2C2F34)
private val barLabel = Color(0xFFFFFFFF)

/**
 * The colors a progress bar falls back to when neither the game nor the skin named one for it: the
 * active skin's `progressBar` section, then the mirrored defaults above. Everything the skin does
 * name (the vital bars, anything inside a `<skin controls=...>`) resolves through [getColorGroup]
 * first and never reaches this.
 */
@Composable
internal fun defaultProgressBarColors(): ColorGroup {
    val isDark = LocalDarkTheme.current
    val entry = LocalSkin.current.getIgnoringCase("progressBar")
    return ColorGroup(
        text =
            entry
                ?.color
                .forMode(isDark)
                ?.toWarlockColor()
                .toColor(default = barLabel),
        bar =
            entry
                ?.bar
                .forMode(isDark)
                ?.toWarlockColor()
                .toColor(default = if (isDark) barOnDark else barOnLight),
        background =
            entry?.background.forMode(isDark)?.toWarlockColor().toColor(
                default = if (isDark) trackOnDark else trackOnLight,
            ),
    )
}

@Composable
internal fun SkinObject?.getColorGroup(): ColorGroup {
    val isDark = LocalDarkTheme.current
    return ColorGroup(
        text =
            this
                ?.color
                .forMode(isDark)
                ?.toWarlockColor()
                .toColor(),
        bar =
            this
                ?.bar
                .forMode(isDark)
                ?.toWarlockColor()
                .toColor(),
        background =
            this
                ?.background
                .forMode(isDark)
                ?.toWarlockColor()
                .toColor(),
    )
}
