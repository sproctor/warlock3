package warlockfe.warlock3.compose.util

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import warlockfe.warlock3.compose.components.DEFAULT_PANEL_SCALE
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.ResolvedStyle
import warlockfe.warlock3.core.text.resolve
import warlockfe.warlock3.core.text.toLayer

/**
 * The resolved base ("default text") style — what un-styled game text renders as, plus the base for
 * the input line and status chrome that used to read `presets["default"]`. Provided by the game
 * views from the character/global/skin base cascade.
 *
 * Carries the whole resolved style, font included: project it with `toStyleDefinition()` for the
 * colour/decoration half or `toFontConfig()` for the family/size/weight half at the point of use.
 */
val LocalBaseStyle = staticCompositionLocalOf { resolve(listOf(SAFE_DEFAULT_STYLE.toLayer())) }

/**
 * The character's panel defaults: the text style widgets inside panel windows draw with, and the
 * multiplier applied to the pixel-sized widget geometry the game sends. An individual panel window
 * may override either.
 */
data class PanelDefaults(
    /**
     * The panel font. Panels are chrome rather than prose, so they deliberately don't follow
     * [LocalBaseStyle]. Null means the built-in compact base (PANEL_BASE_FONT_SIZE).
     */
    val font: FontConfig? = null,
    /**
     * The panel scale. It never scales the font, and never widgets the game sized as a percentage
     * of the panel.
     */
    val scale: Float = DEFAULT_PANEL_SCALE,
)

/** The active character's [PanelDefaults]. Provided by the game views. */
val LocalPanelDefaults = staticCompositionLocalOf { PanelDefaults() }

/**
 * The resolved text style every widget in the current panel draws with, published once by
 * `PanelContent`/`DesktopPanelContent` so the widget composables share one knob instead of each
 * hardcoding a size. Wrayth gives each widget type its own font; we deliberately use one.
 */
val LocalPanelTextStyle = staticCompositionLocalOf { TextStyle.Default }
