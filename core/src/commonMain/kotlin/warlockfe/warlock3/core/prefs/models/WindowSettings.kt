package warlockfe.warlock3.core.prefs.models

import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.WarlockColor

/**
 * A window's full settings as seen by the UI: the open flag that lives in SQLite
 * ([WindowSettingsEntity]) combined with the styling that lives in the character's TOML config
 * (`windows` section). [WindowSettingsRepository.observeWindowSettings] merges the two by window
 * name and emits this.
 */
data class WindowSettings(
    val characterId: String,
    val name: String,
    // Whether the window is in the layout; its arrangement lives in the docking layout JSON.
    val open: Boolean = false,
    val textColor: WarlockColor,
    val backgroundColor: WarlockColor,
    val font: FontConfig?,
    val monoFont: FontConfig?,
    // Panel windows only: overrides the character-wide panel scale. Null = use the character's.
    val scale: Float? = null,
    val nameFilter: Boolean,
    // The user closed this window and has not asked for it back, so the game must not reopen it.
    val hidden: Boolean = false,
    // bold mirrors weight == 700 for legacy readers; tri-state (null = inherit) otherwise, matching
    // WindowStyleConfig so a round trip through the appearance editor doesn't lose an explicit "off".
    val bold: Boolean = false,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val weight: Int? = null,
    val textColorRef: String? = null,
    val backgroundColorRef: String? = null,
)
