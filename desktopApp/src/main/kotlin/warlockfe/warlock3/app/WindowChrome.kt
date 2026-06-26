package warlockfe.warlock3.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.window.styling.TitleBarStyle

/**
 * Reshape Jewel's min/maximize/close buttons into circular, GNOME-style controls: each gets a constant
 * subtle background - the title bar's own content color at low alpha, so it tracks the theme - clipped
 * to a circle and inset (via the icon button padding, which Jewel applies before drawing the
 * background) so the buttons read as separate round pills. Jewel's glyphs and theme colors are kept.
 *
 * [active] is the window's focus state. When the window is in the background its title bar lightens, so
 * the backgrounds drop to a lower alpha (rather than riding that lighter bar) and the buttons recede.
 *
 * The styles are rebuilt field-by-field because Jewel's styling classes expose no `copy()`.
 */
internal fun TitleBarStyle.withRoundedPaneButtons(active: Boolean): TitleBarStyle {
    val content = colors.content
    val alpha = if (active) 1f else 0.5f

    fun rounded(button: IconButtonStyle): IconButtonStyle {
        val c = button.colors
        return IconButtonStyle(
            colors =
                IconButtonColors(
                    foregroundSelectedActivated = c.foregroundSelectedActivated,
                    background = content.copy(alpha = 0.10f * alpha),
                    backgroundDisabled = c.backgroundDisabled,
                    backgroundSelected = c.backgroundSelected,
                    backgroundSelectedActivated = c.backgroundSelectedActivated,
                    backgroundFocused = content.copy(alpha = 0.20f * alpha),
                    backgroundPressed = content.copy(alpha = 0.28f * alpha),
                    backgroundHovered = content.copy(alpha = 0.20f * alpha),
                    border = c.border,
                    borderDisabled = c.borderDisabled,
                    borderSelected = c.borderSelected,
                    borderSelectedActivated = c.borderSelectedActivated,
                    borderFocused = c.borderFocused,
                    borderPressed = c.borderPressed,
                    borderHovered = c.borderHovered,
                ),
            metrics =
                IconButtonMetrics(
                    cornerSize = CornerSize(percent = 50),
                    borderWidth = button.metrics.borderWidth,
                    padding = PaddingValues(6.dp),
                    minSize = button.metrics.minSize,
                ),
        )
    }
    return TitleBarStyle(
        colors = colors,
        metrics = metrics,
        icons = icons,
        dropdownStyle = dropdownStyle,
        iconButtonStyle = iconButtonStyle,
        paneButtonStyle = rounded(paneButtonStyle),
        paneCloseButtonStyle = rounded(paneCloseButtonStyle),
    )
}
