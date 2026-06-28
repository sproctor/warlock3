package warlockfe.warlock3.compose.util

import androidx.compose.runtime.staticCompositionLocalOf
import warlockfe.warlock3.core.text.StyleDefinition

/**
 * The resolved style presets for the active character: the skin's default presets overridden by the
 * user's saved presets (the same map that styles stream text, e.g. the user-configurable "link"
 * preset). Provided where the presets flow is available; defaults to empty.
 */
val LocalStyleMap = staticCompositionLocalOf<Map<String, StyleDefinition>> { emptyMap() }
