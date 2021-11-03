package cc.warlock.warlock3.core.text

import cc.warlock.warlock3.core.util.toWarlockColor

data class WarlockColor(val argb: Long) {
    constructor(value: String) : this(value.toWarlockColor()?.argb ?: -1) // a bit hacky
    constructor(red: Int, green: Int, blue: Int, alpha: Int = 0xFF) : this(alpha.toLong() * 0x1000000L + red.toLong() * 0x10000L + green.toLong() * 0x100L + blue.toLong())

    companion object {
        val Unspecified = WarlockColor(-1)
    }
}

fun WarlockColor.isUnspecified(): Boolean = !isSpecified()
fun WarlockColor.isSpecified(): Boolean = argb >= 0
