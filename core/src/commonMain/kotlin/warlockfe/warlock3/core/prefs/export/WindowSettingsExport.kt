package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.WindowLocation

@Serializable
data class WindowSettingsExport(
    val name: String,
    val width: Int?,
    val height: Int?,
    // Only set while the window is in the layout: older builds read location != null as docked
    // (their parsers drop the newer fields), so a closed window must not carry these.
    val location: WindowLocation?,
    val position: Int?,
    // Whether the window is in the layout. Null for exports written before the flag existed,
    // where placed meant location != null.
    val open: Boolean? = null,
    // A closed window's remembered placement, split from location/position so an old build
    // importing this export cannot resurrect closed windows.
    val rememberedLocation: WindowLocation? = null,
    val rememberedPosition: Int? = null,
    val textColor: WarlockColor,
    val backgroundColor: WarlockColor,
    val font: FontConfig? = null,
    val monoFont: FontConfig? = null,
    val nameFilter: Boolean = false,
    // The user closed this window, so the game must not reopen it. Defaults false for exports written
    // before the flag existed.
    val hidden: Boolean = false,
    val bold: Boolean = false,
    val italic: Boolean? = false,
    val underline: Boolean? = false,
    val weight: Int? = null,
    // Skin-palette slot a color references (so it tracks the skin); null = the color above is a literal.
    val textColorRef: String? = null,
    val backgroundColorRef: String? = null,
)
