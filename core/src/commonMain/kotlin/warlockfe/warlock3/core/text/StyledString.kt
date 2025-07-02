package warlockfe.warlock3.core.text

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

data class StyledString(val substrings: ImmutableList<StyledStringLeaf>) {
    constructor(text: String, styles: List<WarlockStyle> = emptyList())
            : this(persistentListOf<StyledStringSubstring>(StyledStringSubstring(text, styles)))

    constructor(text: String, style: WarlockStyle) : this(text, listOf(style))

    operator fun plus(string: StyledString): StyledString {
        return StyledString((substrings + string.substrings).toPersistentList())
    }

    fun applyStyle(style: WarlockStyle): StyledString {
        return copy(substrings = substrings.map { it.applyStyle(style) }.toPersistentList())
    }

    override fun toString(): String {
        val builder = StringBuilder()
        substrings.forEach { substring ->
            if (substring is StyledStringSubstring) {
                builder.append(substring.text)
            }
        }
        return builder.toString()
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

fun StyledString.isBlank(): Boolean {
    return substrings.all { it.isBlank() }
}

fun StyledStringLeaf.isBlank(): Boolean {
    return when (this) {
        is StyledStringVariable -> false
        is StyledStringSubstring -> text.isBlank()
    }
}
