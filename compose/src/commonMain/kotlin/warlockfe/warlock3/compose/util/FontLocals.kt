package warlockfe.warlock3.compose.util

import androidx.compose.runtime.staticCompositionLocalOf
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
 * previews and dialog windows that render outside a stream.
 */
val LocalMonoFont = staticCompositionLocalOf<FontConfig?> { null }
