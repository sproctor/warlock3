package warlockfe.warlock3.core.text

import warlockfe.warlock3.core.util.toWarlockColor

data class WarlockColor(val argb: Long) {
    constructor(value: String) : this(value.toWarlockColor()?.argb ?: -1)
    constructor(red: Int, green: Int, blue: Int, alpha: Int = 0xFF)
            : this(argb = alpha * 0x1000000L + red * 0x10000L + green * 0x100L + blue)

    companion object {
        val Unspecified = WarlockColor(-1)
    }
}

fun WarlockColor.isUnspecified(): Boolean = argb == -1L
fun WarlockColor.isSpecified(): Boolean = argb != -1L

fun WarlockColor.specifiedOrNull(): WarlockColor? =
    if (isSpecified()) this else null

fun WarlockColor.ifUnspecified(defaultColor: WarlockColor): WarlockColor {
    if (isSpecified()) return this
    return defaultColor
}

fun WarlockColor.toHexString(): String? {
    if (isUnspecified()) return null
    return "#" + argb.toString(16)
}