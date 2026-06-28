package warlockfe.warlock3.core.prefs.models

import warlockfe.warlock3.core.text.WarlockColor

/**
 * A progress bar's per-id overrides as seen by the UI: the color overrides plus an optional font
 * (family/size/weight). All of it lives in the character's TOML config (`progressBars` section);
 * [warlockfe.warlock3.core.prefs.repositories.ProgressBarSettingRepository.observeByCharacter] emits
 * this. The legacy Room [ProgressBarSettingEntity] persists only colors and is kept for first-run
 * migration.
 */
data class ProgressBarSetting(
    val characterId: String,
    val id: String,
    val barColor: WarlockColor,
    val backgroundColor: WarlockColor,
    val textColor: WarlockColor,
    val fontFamily: String?,
    val fontSize: Float?,
    val fontWeight: Int?,
)
