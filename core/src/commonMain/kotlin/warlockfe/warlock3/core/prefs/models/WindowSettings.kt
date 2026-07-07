package warlockfe.warlock3.core.prefs.models

import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.WindowLocation

/**
 * A window's full settings as seen by the UI: the geometry that lives in SQLite
 * ([WindowSettingsEntity]) combined with the styling that lives in the character's TOML config
 * (`windows` section). [WindowSettingsRepository.observeWindowSettings] merges the two by window
 * name and emits this.
 */
data class WindowSettings(
    val characterId: String,
    val name: String,
    val width: Int?,
    val height: Int?,
    val location: WindowLocation?,
    val position: Int?,
    val textColor: WarlockColor,
    val backgroundColor: WarlockColor,
    val font: FontConfig?,
    val monoFont: FontConfig?,
    val nameFilter: Boolean,
)
