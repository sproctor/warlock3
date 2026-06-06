package warlockfe.warlock3.core.prefs.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import dev.eav.tomlkt.TomlComment
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.isUnspecified
import warlockfe.warlock3.core.util.toWarlockColor

/**
 * Serializes a [WarlockColor] as a human-friendly hex string (e.g. "#ffff0000", in AARRGGBB order)
 * or the literal "default" for an unspecified color. This is what makes the on-disk config files
 * pleasant to hand-edit, instead of exposing the raw packed argb long.
 */
object WarlockColorAsHexSerializer : KSerializer<WarlockColor> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("WarlockColor", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: WarlockColor) {
        val text =
            if (value.isUnspecified()) {
                "default"
            } else {
                // Pad to a full 8 hex digits so the alpha channel always round-trips.
                "#" + value.argb.toString(16).padStart(8, '0')
            }
        encoder.encodeString(text)
    }

    override fun deserialize(decoder: Decoder): WarlockColor {
        val text = decoder.decodeString()
        return if (text.isEmpty() || text == "default") {
            WarlockColor.Unspecified
        } else {
            text.toWarlockColor() ?: WarlockColor.Unspecified
        }
    }
}

/**
 * The root of a per-character config file (or `global.toml`). Holds the human-editable
 * highlights, names, and variables that previously lived in the SQLite database.
 *
 * Field order matters for TOML output: the scalar [character] is emitted first, the
 * array-of-tables sections next, and the [variables] table last.
 */
@Serializable
data class CharacterConfig(
    @TomlComment("Character id this file applies to ('global' means shared by all characters). Do not change.")
    val character: String = "",
    val highlights: List<HighlightConfig> = emptyList(),
    val names: List<NameConfig> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
)

@Serializable
data class HighlightConfig(
    val id: String? = null,
    val pattern: String = "",
    val isRegex: Boolean = false,
    val matchPartialWord: Boolean = false,
    val ignoreCase: Boolean = false,
    val sound: String? = null,
    val styles: List<HighlightStyleConfig> = emptyList(),
)

@Serializable
data class HighlightStyleConfig(
    @TomlComment("Regex capture group this style applies to (0 = the whole match).")
    val group: Int = 0,
    @Serializable(WarlockColorAsHexSerializer::class)
    val textColor: WarlockColor = WarlockColor.Unspecified,
    @Serializable(WarlockColorAsHexSerializer::class)
    val backgroundColor: WarlockColor = WarlockColor.Unspecified,
    val entireLine: Boolean = false,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fontFamily: String? = null,
    val fontSize: Float? = null,
    val fontWeight: Int? = null,
)

@Serializable
data class NameConfig(
    val id: String? = null,
    val text: String = "",
    val sound: String? = null,
    @Serializable(WarlockColorAsHexSerializer::class)
    val textColor: WarlockColor = WarlockColor.Unspecified,
    @Serializable(WarlockColorAsHexSerializer::class)
    val backgroundColor: WarlockColor = WarlockColor.Unspecified,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fontFamily: String? = null,
    val fontSize: Float? = null,
    val fontWeight: Int? = null,
)
