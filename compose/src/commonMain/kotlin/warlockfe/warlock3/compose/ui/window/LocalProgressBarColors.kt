package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.compositionLocalOf
import warlockfe.warlock3.core.prefs.models.ProgressBarSetting
import warlockfe.warlock3.core.text.WarlockColor

/**
 * Carries the current character's progress-bar overrides (keyed by progress-bar id): the color
 * overrides plus an optional font, and callbacks to persist new colors or a new font. Provided high
 * in the game view so the deeply-nested dialog content can read/write per-id settings without
 * threading callbacks through every layer.
 */
data class ProgressBarColorState(
    val settings: Map<String, ProgressBarSetting>,
    val saveColors: (
        id: String,
        barColor: WarlockColor,
        backgroundColor: WarlockColor,
        textColor: WarlockColor,
    ) -> Unit,
    val saveFont: (
        id: String,
        fontFamily: String?,
        fontSize: Float?,
        fontWeight: Int?,
    ) -> Unit,
)

val LocalProgressBarColors =
    compositionLocalOf {
        ProgressBarColorState(
            settings = emptyMap(),
            saveColors = { _, _, _, _ -> },
            saveFont = { _, _, _, _ -> },
        )
    }
