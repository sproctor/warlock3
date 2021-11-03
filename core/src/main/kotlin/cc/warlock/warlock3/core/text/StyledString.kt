package cc.warlock.warlock3.core.text

data class StyledString(val substrings: List<StyledStringLeaf>) {
    constructor(text: String, styles: List<WarlockStyle> = emptyList())
            : this(listOf(StyledStringSubstring(text, styles)))
    constructor(text: String, style: WarlockStyle)
    : this(text, listOf(style))

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

sealed class StyledStringLeaf(val styles: List<WarlockStyle>)
class StyledStringSubstring(val text: String, styles: List<WarlockStyle>) : StyledStringLeaf(styles)
class StyledStringVariable(val name: String, styles: List<WarlockStyle>) : StyledStringLeaf(styles)

fun StyledStringLeaf.applyStyle(style: WarlockStyle): StyledStringLeaf {
    return when (this) {
        is StyledStringSubstring -> StyledStringSubstring(text = text, styles = styles + style)
        is StyledStringVariable -> StyledStringVariable(name = name, styles = styles + style)
    }
}
