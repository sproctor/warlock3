package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable
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
    val fontFamily: String?,
    val fontSize: Float?,
)
