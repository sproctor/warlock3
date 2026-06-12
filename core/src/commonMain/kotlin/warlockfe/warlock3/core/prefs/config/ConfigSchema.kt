package warlockfe.warlock3.core.prefs.config

import dev.eav.tomlkt.TomlComment
import dev.eav.tomlkt.TomlInline
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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

    override fun serialize(
        encoder: Encoder,
        value: WarlockColor,
    ) {
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
    val aliases: List<AliasConfig> = emptyList(),
    val alterations: List<AlterationConfig> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    // Keyed by key-combo string (e.g. "ctrl alt f1"); value is the macro action.
    val macros: Map<String, String> = emptyMap(),
    // Keyed by preset id (e.g. "speech", "bold"); the default styles still come from the skin.
    val presets: Map<String, PresetStyleConfig> = emptyMap(),
    // Keyed by progress-bar id (e.g. "health", "mana").
    val progressBars: Map<String, ProgressBarConfig> = emptyMap(),
    // Keyed by window name; holds the styling half of the window settings (geometry stays in SQLite).
    val windows: Map<String, WindowStyleConfig> = emptyMap(),
    val settings: CharacterSettingsConfig = CharacterSettingsConfig(),
)

@Serializable
data class AliasConfig(
    val id: String? = null,
    val pattern: String = "",
    val replacement: String = "",
)

@Serializable
data class AlterationConfig(
    val id: String? = null,
    val pattern: String = "",
    val sourceStream: String? = null,
    val destinationStream: String? = null,
    val result: String? = null,
    val ignoreCase: Boolean = false,
    val keepOriginal: Boolean = false,
)

@Serializable
data class PresetStyleConfig(
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
data class ProgressBarConfig(
    @Serializable(WarlockColorAsHexSerializer::class)
    val barColor: WarlockColor = WarlockColor.Unspecified,
    @Serializable(WarlockColorAsHexSerializer::class)
    val backgroundColor: WarlockColor = WarlockColor.Unspecified,
    @Serializable(WarlockColorAsHexSerializer::class)
    val textColor: WarlockColor = WarlockColor.Unspecified,
)

@Serializable
data class WindowStyleConfig(
    @Serializable(WarlockColorAsHexSerializer::class)
    val textColor: WarlockColor = WarlockColor.Unspecified,
    @Serializable(WarlockColorAsHexSerializer::class)
    val backgroundColor: WarlockColor = WarlockColor.Unspecified,
    val fontFamily: String? = null,
    val fontSize: Float? = null,
    val fontWeight: Int? = null,
    val nameFilter: Boolean = false,
)

@Serializable
data class CharacterSettingsConfig(
    val typeahead: Int? = null,
    val scriptCommandPrefix: String? = null,
)

// Per-section file wrappers. Each per-character section is stored in its own file under
// characters/<gameCode>/<name>/ (e.g. highlights.toml, variables.toml); these wrap the section so the
// TOML root is a table. settings.toml uses [CharacterSettingsConfig] directly (its fields are the root).

@Serializable
internal data class HighlightsFile(
    val highlights: List<HighlightConfig> = emptyList(),
)

@Serializable
internal data class NamesFile(
    val names: List<NameConfig> = emptyList(),
)

@Serializable
internal data class AliasesFile(
    val aliases: List<AliasConfig> = emptyList(),
)

@Serializable
internal data class AlterationsFile(
    val alterations: List<AlterationConfig> = emptyList(),
)

@Serializable
internal data class VariablesFile(
    val variables: Map<String, String> = emptyMap(),
)

@Serializable
internal data class MacrosFile(
    val macros: Map<String, String> = emptyMap(),
)

@Serializable
internal data class PresetsFile(
    val presets: Map<String, PresetStyleConfig> = emptyMap(),
)

@Serializable
internal data class ProgressBarsFile(
    val progressBars: Map<String, ProgressBarConfig> = emptyMap(),
)

@Serializable
internal data class WindowsFile(
    val windows: Map<String, WindowStyleConfig> = emptyMap(),
)

@Serializable
data class HighlightConfig(
    val id: String? = null,
    val pattern: String = "",
    val isRegex: Boolean = false,
    val matchPartialWord: Boolean = false,
    val ignoreCase: Boolean = false,
    val sound: String? = null,
    // Emit styles as an inline array of inline tables (styles = [{ group = 0, ... }]) instead of a
    // nested [[highlights.styles]] sub-section, which is far easier to read and hand-edit.
    @TomlInline
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

/**
 * Root of `client.toml`: the client-wide application settings that used to live in the
 * `clientsetting` SQLite table. Enums are stored by their `name` so the file stays readable;
 * window geometry (`width`/`height`) and machine state (`lastUsername`) deliberately stay in SQLite.
 */
@Serializable
data class ClientConfig(
    val theme: String? = null,
    val scrollback: Int? = null,
    val markLinks: Boolean = true,
    val showImages: Boolean = true,
    val logPath: String? = null,
    val logType: String? = null,
    val logTimestamps: Boolean = true,
    val skinFile: String? = null,
    val releaseChannel: String? = null,
)

/**
 * Root of `connections.toml`: the human-readable registry of known characters and the saved
 * connection profiles (with per-connection proxy settings folded in). Mostly machine-written, but
 * interesting to read and occasionally hand-edit.
 */
@Serializable
data class ConnectionRegistryConfig(
    val characters: List<CharacterEntry> = emptyList(),
    val connections: List<ConnectionConfig> = emptyList(),
)

@Serializable
data class CharacterEntry(
    val id: String = "",
    val gameCode: String = "",
    val name: String = "",
)

@Serializable
data class ConnectionConfig(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val gameCode: String = "",
    val character: String = "",
    val proxyEnabled: Boolean = false,
    val proxyLaunchCommand: String? = null,
    val proxyHost: String? = null,
    val proxyPort: String? = null,
)
