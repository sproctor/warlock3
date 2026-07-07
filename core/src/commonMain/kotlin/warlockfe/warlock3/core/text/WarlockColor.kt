package warlockfe.warlock3.core.text

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import warlockfe.warlock3.core.util.toWarlockColor

@Serializable
data class WarlockColor(
    val argb: Long,
) {
    constructor(value: String) : this(value.toWarlockColor()?.argb ?: -1)
    constructor(red: Int, green: Int, blue: Int, alpha: Int = 0xFF) :
        this(argb = alpha * 0x1000000L + red * 0x10000L + green * 0x100L + blue)

    override fun toString(): String {
        if (isUnspecified()) return "WarlockColor.Unspecified"
        return "WarlockColor(argb=0x${argb.toString(16)})"
    }

    companion object {
        val Unspecified = WarlockColor(-1)
    }
}

fun WarlockColor.isUnspecified(): Boolean = argb == -1L

fun WarlockColor.isSpecified(): Boolean = argb != -1L

fun WarlockColor.specifiedOrNull(): WarlockColor? = if (isSpecified()) this else null

fun WarlockColor.ifUnspecified(defaultColor: WarlockColor): WarlockColor {
    if (isSpecified()) return this
    return defaultColor
}

fun WarlockColor.toHexString(): String? {
    if (isUnspecified()) return null
    return "#" + argb.toString(16)
}

object WarlockColorAsHexSerializer : KSerializer<WarlockColor> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("WarlockColor", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: WarlockColor,
    ) {
        encoder.encodeString(value.toHexString() ?: "default")
    }

    override fun deserialize(decoder: Decoder): WarlockColor {
        val text = decoder.decodeString()
        return if (text.isBlank() || text == "default") {
            WarlockColor.Unspecified
        } else {
            text.toWarlockColor() ?: WarlockColor.Unspecified
        }
    }
}
