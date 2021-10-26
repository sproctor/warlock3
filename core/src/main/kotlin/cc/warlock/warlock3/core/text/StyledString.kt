package cc.warlock.warlock3.core.text

data class StyledString(val substrings: List<StyledStringLeaf>) {
    constructor(text: String, style: WarlockStyle? = null)
            : this(listOf(StyledStringSubstring(text, style)))

    operator fun plus(string: StyledString): StyledString {
        return StyledString(substrings + string.substrings)
    }

    fun toPlainString(): String {
        val builder = StringBuilder()
        substrings.forEach { substring ->
            if (substring is StyledStringSubstring) {
                builder.append(substring.text)
            }
        }
        return builder.toString()
    }

    fun applyStyle(style: WarlockStyle): StyledString {
        return copy(substrings = substrings.map { it.applyStyle(style) })
    }
}

sealed class StyledStringLeaf
data class StyledStringSubstring(val text: String, val style: WarlockStyle?) : StyledStringLeaf()
data class StyledStringVariable(val name: String, val style: WarlockStyle?) : StyledStringLeaf()

fun StyledStringLeaf.applyStyle(style: WarlockStyle): StyledStringLeaf {
    return when (this) {
        is StyledStringSubstring -> copy(style = flattenStyles(listOfNotNull(style, this.style)))
        is StyledStringVariable -> copy(style = flattenStyles(listOfNotNull(style, this.style)))
    }
}

data class WarlockStyle(
    val textColor: WarlockColor = WarlockColor(-1),
    val backgroundColor: WarlockColor = WarlockColor(-1),
    val entireLine: Boolean = false,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val monospace: Boolean = false
) {
    fun mergeWith(other: WarlockStyle): WarlockStyle {
        return WarlockStyle(
            textColor = if (textColor.isSpecified()) textColor else other.textColor,
            backgroundColor = if (backgroundColor.isSpecified()) backgroundColor else other.backgroundColor,
            entireLine = entireLine || other.entireLine,
            bold = bold || other.bold,
            italic = italic || other.italic,
            underline = underline || other.underline,
            monospace = monospace || other.monospace,
        )
    }
}

data class WarlockColor(val argb: Long) {
    constructor(value: String) : this(value.toLongOrNull() ?: -1)
    constructor(red: Int, green: Int, blue: Int, alpha: Int = 0xFF) : this(alpha.toLong() * 0x1000000L + red.toLong() * 0x10000L + green.toLong() * 0x100L + blue.toLong())

    companion object {
        val Unspecified = WarlockColor(-1)
    }
}

fun WarlockColor.isUnspecified(): Boolean = !isSpecified()
fun WarlockColor.isSpecified(): Boolean = argb >= 0

fun flattenStyles(styles: List<WarlockStyle>): WarlockStyle? {
    return styles
        .reduceOrNull { acc, warlockStyle ->
            acc.mergeWith(warlockStyle)
        }
}