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
    val location: WindowLocation?,
    val position: Int?,
    val textColor: WarlockColor,
    val backgroundColor: WarlockColor,
    val font: FontConfig? = null,
    val monoFont: FontConfig? = null,
    val nameFilter: Boolean = false,
    val bold: Boolean = false,
    val italic: Boolean? = false,
    val underline: Boolean? = false,
    val weight: Int? = null,
    // Skin-palette slot a color references (so it tracks the skin); null = the color above is a literal.
    val textColorRef: String? = null,
    val backgroundColorRef: String? = null,
)
