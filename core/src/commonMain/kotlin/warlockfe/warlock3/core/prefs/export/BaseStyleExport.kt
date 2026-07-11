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
    val italic: Boolean = false,
    val underline: Boolean = false,
    val font: FontConfig? = null,
    val monoFont: FontConfig? = null,
)
