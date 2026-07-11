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
    val monospace: Boolean,
    // Per-item font (presets only today). Defaulted so exports written before these existed still load,
    // and so highlight/name styles - which carry no font - can omit them.
    val weight: Int? = null,
    val fontFamily: String? = null,
    val fontSize: Float? = null,
)
