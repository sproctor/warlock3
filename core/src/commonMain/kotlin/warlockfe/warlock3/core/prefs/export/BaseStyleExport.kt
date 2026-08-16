package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.WarlockColor

/**
 * The character's base ("default text") style plus its default and monospace fonts, which live in the
 * character settings rather than the presets. Carried as an optional block on [CharacterExport]: absent
 * (null) on exports written before the base style was exportable, so import can tell "no base in this
 * file, keep the target's" apart from "this base explicitly has no color/font".
 */
@Serializable
data class BaseStyleExport(
    val textColor: WarlockColor = WarlockColor.Unspecified,
    val backgroundColor: WarlockColor = WarlockColor.Unspecified,
    val italic: Boolean? = false,
    val underline: Boolean? = false,
    val font: FontConfig? = null,
    val monoFont: FontConfig? = null,
    // The panel-window font and pixel-geometry scale. Null here means the same thing it means for the
    // two fonts above, and is handled the same way: a MERGE import keeps whatever the target has, while
    // a REPLACE import takes a present base wholesale and so clears them. Null cannot distinguish "this
    // export predates the field" from "this character set no panel font" - only the whole block being
    // absent carries "keep the target's", which is the distinction this class exists to draw.
    val panelFont: FontConfig? = null,
    val panelScale: Float? = null,
    // Skin-palette slot a color references (so it tracks the skin); null = the color above is a literal.
    val textColorRef: String? = null,
    val backgroundColorRef: String? = null,
)
