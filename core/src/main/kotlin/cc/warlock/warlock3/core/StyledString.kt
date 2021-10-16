package cc.warlock.warlock3.core

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
}

sealed class StyledStringLeaf
data class StyledStringSubstring(val text: String, val style: WarlockStyle?) : StyledStringLeaf()
data class StyledStringVariable(val name: String, val style: WarlockStyle?) : StyledStringLeaf()

data class WarlockStyle(
    val textColor: WarlockColor? = null,
    val backgroundColor: WarlockColor? = null,
    val isEntireLineBackground: Boolean = false,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val monospace: Boolean = false
) {
    fun mergeWith(other: WarlockStyle): WarlockStyle {
        return WarlockStyle(
            textColor = textColor ?: other.textColor,
            backgroundColor = backgroundColor ?: other.backgroundColor,
            isEntireLineBackground = isEntireLineBackground || other.isEntireLineBackground,
            bold = bold || other.bold,
            italic = italic || other.italic,
            underline = underline || other.underline,
            monospace = monospace || other.monospace,
        )
    }
}

data class WarlockColor(val red: Int, val green: Int, val blue: Int)

fun flattenStyles(styles: List<WarlockStyle>): WarlockStyle? {
    return styles
        .reduceOrNull { acc, warlockStyle ->
            acc.mergeWith(warlockStyle)
        }
}