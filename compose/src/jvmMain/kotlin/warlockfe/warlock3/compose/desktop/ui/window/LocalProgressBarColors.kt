package warlockfe.warlock3.compose.desktop.ui.window

import androidx.compose.runtime.staticCompositionLocalOf
import warlockfe.warlock3.core.prefs.models.ProgressBarSettingEntity
import warlockfe.warlock3.core.text.WarlockColor

/**
 * Carries the current character's progress-bar color overrides (keyed by progress-bar id) and a
 * callback to persist new colors. Provided high in the desktop game view so the deeply-nested
 * dialog content can read/write per-id colors without threading callbacks through every layer.
 */
data class ProgressBarColorState(
    val settings: Map<String, ProgressBarSettingEntity>,
    val saveColors: (
        id: String,
        barColor: WarlockColor,
        backgroundColor: WarlockColor,
        textColor: WarlockColor,
    ) -> Unit,
)

val LocalProgressBarColors =
    staticCompositionLocalOf {
        ProgressBarColorState(settings = emptyMap()) { _, _, _, _ -> }
    }
