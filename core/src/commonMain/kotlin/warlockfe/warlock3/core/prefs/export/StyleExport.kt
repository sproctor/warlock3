package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable
import warlockfe.warlock3.core.text.WarlockColor

@Serializable
data class StyleExport(
    val textColor: WarlockColor,
    val backgroundColor: WarlockColor,
    val entireLine: Boolean,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val fontFamily: String?,
    val fontSize: Float?,
)
