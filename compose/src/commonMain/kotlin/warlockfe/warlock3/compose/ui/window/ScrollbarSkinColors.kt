package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import warlockfe.warlock3.compose.model.forMode
import warlockfe.warlock3.compose.util.LocalDarkTheme
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.util.getIgnoringCase
import warlockfe.warlock3.core.util.toWarlockColor

/**
 * Stream scrollbar colors from the active skin's `scrollbar` section, resolved for the current
 * light/dark mode. Shared by the desktop and mobile scrollbars so both follow the skin. The bundled
 * default skin ports IntelliJ's values: a ~10%-alpha track gutter and a low-alpha thumb. Colors may
 * carry their own alpha (the skin hex supports `#AARRGGBB`).
 */
data class ScrollbarSkinColors(
    /** The track behind the thumb. Low alpha; the desktop view fades it in on hover/drag. */
    val gutter: Color,
    /** The drag handle. */
    val thumb: Color,
)

/**
 * The [ScrollbarSkinColors] from the active skin's `scrollbar` section, resolved for the current
 * [light/dark mode][LocalDarkTheme]. Falls back to IntelliJ's track/thumb if a skin omits the section.
 */
val scrollbarSkinColors: ScrollbarSkinColors
    @Composable
    @ReadOnlyComposable
    get() {
        val children = LocalSkin.current.getIgnoringCase("scrollbar")?.children
        val isDark = LocalDarkTheme.current

        fun color(
            name: String,
            default: Color,
        ): Color =
            children
                ?.getIgnoringCase(name)
                ?.color
                .forMode(isDark)
                ?.toWarlockColor()
                .toColor(default)

        return ScrollbarSkinColors(
            gutter = color("gutter", default = Color(0x1A808080)),
            thumb = color("thumb", default = Color(0x59808080)),
        )
    }
