package warlockfe.warlock3.compose.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * A skin color. In skin.json this is either a single hex string (applies to both light and dark
 * mode) or a per-mode object `{ "light": "#...", "dark": "#..." }`. Resolve with [forMode].
 */
@Serializable(with = SkinColorSerializer::class)
data class SkinColor(
    val light: String? = null,
    val dark: String? = null,
)

/** Resolves the hex string for the current mode, falling back to the other mode if one is absent. */
fun SkinColor?.forMode(isDark: Boolean): String? = this?.let { if (isDark) (dark ?: light) else (light ?: dark) }

object SkinColorSerializer : KSerializer<SkinColor> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("SkinColor") {
            element<String?>("light")
            element<String?>("dark")
        }

    override fun deserialize(decoder: Decoder): SkinColor {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("SkinColor can only be read from JSON")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                val value = element.contentOrNull
                SkinColor(light = value, dark = value)
            }

            is JsonObject -> {
                SkinColor(
                    light = (element["light"] as? JsonPrimitive)?.contentOrNull,
                    dark = (element["dark"] as? JsonPrimitive)?.contentOrNull,
                )
            }

            else -> {
                throw SerializationException("Expected a hex string or a { light, dark } object")
            }
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: SkinColor,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw SerializationException("SkinColor can only be written to JSON")
        if (value.light == value.dark) {
            jsonEncoder.encodeJsonElement(JsonPrimitive(value.light))
        } else {
            jsonEncoder.encodeJsonElement(
                buildJsonObject {
                    value.light?.let { put("light", it) }
                    value.dark?.let { put("dark", it) }
                },
            )
        }
    }
}
