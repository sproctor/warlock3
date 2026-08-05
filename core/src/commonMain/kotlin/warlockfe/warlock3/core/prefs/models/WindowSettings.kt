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
    // Where the window lives when [open], and its remembered placement when closed.
    val location: WindowLocation?,
    val position: Int?,
    // Whether the window is in the layout; closed windows keep location/position remembered.
    val open: Boolean = false,
    val textColor: WarlockColor,
    val backgroundColor: WarlockColor,
    val font: FontConfig?,
    val monoFont: FontConfig?,
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
