package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable
import warlockfe.warlock3.core.text.WarlockColor

@Serializable
data class StyleExport(
    val textColor: WarlockColor,
    val backgroundColor: WarlockColor,
    // Tri-state (null = inherit); defaulted so pre-tri-state exports (which always wrote true/false)
    // still load and still mean exactly what they said.
    val entireLine: Boolean? = false,
    val bold: Boolean,
    val italic: Boolean? = false,
    val underline: Boolean? = false,
    val monospace: Boolean? = false,
    // Per-item font, carried for presets, highlights, and names alike. Defaulted so exports written
    // before these existed still load.
    val weight: Int? = null,
    val fontFamily: String? = null,
    val fontSize: Float? = null,
    // Skin-palette slot a color references (so it tracks the skin); null = the color above is a literal.
    val textColorRef: String? = null,
    val backgroundColorRef: String? = null,
)
