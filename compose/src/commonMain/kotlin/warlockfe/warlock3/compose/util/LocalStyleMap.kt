package warlockfe.warlock3.compose.util

import androidx.compose.runtime.staticCompositionLocalOf
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.resolve
import warlockfe.warlock3.core.text.toStyleDefinition

/**
 * The named style presets for the active character as sparse [StyleLayer]s: the skin's default presets
 * overridden by the user's saved presets (the same map that styles stream text, e.g. the
 * user-configurable "link" preset). Sparse so per-item fonts and the tri-state background survive to the
 * renderer. Does NOT contain a "default" entry - the base ("default text") style is separate
 * ([LocalBaseStyle]). Provided where the presets flow is available; defaults to empty.
 */
val LocalStyleMap = staticCompositionLocalOf<Map<String, StyleLayer>> { emptyMap() }

/**
 * Resolves the named preset [key] down to a dense [StyleDefinition] for the chrome (the input line) that
 * still consumes the legacy type, falling back to an empty (no-override) style when the preset is absent
 * so it inherits the base. Per-item fonts are dropped in the projection (chrome uses its own font locals).
 */
fun Map<String, StyleLayer>.resolvedStyle(key: String): StyleDefinition =
    this[key]?.let { resolve(listOf(it)).toStyleDefinition() } ?: StyleDefinition()
