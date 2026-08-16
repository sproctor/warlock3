package warlockfe.warlock3.compose.util

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import warlockfe.warlock3.compose.components.DEFAULT_PANEL_SCALE
import warlockfe.warlock3.core.text.FontConfig

/**
 * The resolved base ("default text") color/style — what un-styled game text renders as, plus the base
 * for the input line and status chrome that used to read `presets["default"]`. Provided by the game
 * views from the character/global/skin base cascade. [LocalDefaultFont] carries the base font half.
 */
val LocalBaseStyle = staticCompositionLocalOf { SAFE_DEFAULT_STYLE }

/**
 * The active character's default (normal) font, used as the base text style for game windows unless a
 * window overrides it. Null means "use the platform/theme default". Provided by the game views where
 * the character settings flow is available.
 */
val LocalDefaultFont = staticCompositionLocalOf<FontConfig?> { null }

/**
 * The active character's monospace font, used for text flagged monospace. Null means the generic
 * monospace family. Stream text bakes this in itself (per window); this local is for the settings
 * previews and panel windows that render outside a stream.
 */
val LocalMonoFont = staticCompositionLocalOf<FontConfig?> { null }

/**
 * The active character's panel font: the text style for widgets inside panel windows. Panels are
 * chrome rather than prose, so they deliberately don't follow [LocalDefaultFont]. Null means the
 * built-in compact base (PANEL_BASE_FONT_SIZE); an individual panel window may still override it.
 */
val LocalPanelFont = staticCompositionLocalOf<FontConfig?> { null }

/**
 * The active character's panel scale: the multiplier applied to the pixel-sized widget geometry the
 * game sends for a panel. It never scales the font, and never widgets the game sized as a percentage
 * of the panel. An individual panel window may override it.
 */
val LocalPanelScale = staticCompositionLocalOf { DEFAULT_PANEL_SCALE }

/**
 * The resolved text style every widget in the current panel draws with, published once by
 * `PanelContent`/`DesktopPanelContent` so the widget composables share one knob instead of each
 * hardcoding a size. Wrayth gives each widget type its own font; we deliberately use one.
 */
val LocalPanelTextStyle = staticCompositionLocalOf { TextStyle.Default }
