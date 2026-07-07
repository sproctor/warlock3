package warlockfe.warlock3.compose.util

import androidx.compose.runtime.staticCompositionLocalOf
import warlockfe.warlock3.core.text.FontConfig

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

/**
 * Saves a window's per-window font overrides (keyed by window name), so the window settings dialog can
 * persist them without threading callbacks through the whole window hierarchy. Provided by the game
 * views; a no-op by default.
 */
class WindowFontSaver(
    val saveFont: (windowName: String, font: FontConfig?) -> Unit,
    val saveMonoFont: (windowName: String, font: FontConfig?) -> Unit,
)

val LocalWindowFontSaver = staticCompositionLocalOf { WindowFontSaver({ _, _ -> }, { _, _ -> }) }
