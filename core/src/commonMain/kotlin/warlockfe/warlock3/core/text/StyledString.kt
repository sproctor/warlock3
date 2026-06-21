package warlockfe.warlock3.core.text

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

data class StyledString(
    val substrings: PersistentList<StyledStringLeaf> = persistentListOf(),
) {
    constructor(text: String, styles: List<WarlockStyle> = emptyList()) :
        this(persistentListOf<StyledStringSubstring>(StyledStringSubstring(text, styles)))

    constructor(text: String, style: WarlockStyle) : this(text, listOf(style))

    // addAll on a persistent list appends in O(elements added) with structural sharing, so folding
    // many fragments into one line (e.g. a room description) is O(total) rather than the O(n²) of
    // repeated (a + b) whole-list copies.
    operator fun plus(string: StyledString): StyledString = copy(substrings = substrings.addAll(string.substrings))

    fun applyStyle(style: WarlockStyle): StyledString = copy(substrings = substrings.map { it.applyStyle(style) }.toPersistentList())

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

sealed class StyledStringLeaf(
    val styles: List<WarlockStyle>,
)

class StyledStringSubstring(
    val text: String,
    styles: List<WarlockStyle>,
) : StyledStringLeaf(styles)

class StyledStringVariable(
    val name: String,
    styles: List<WarlockStyle>,
) : StyledStringLeaf(styles)

fun StyledStringLeaf.applyStyle(style: WarlockStyle): StyledStringLeaf =
    when (this) {
        is StyledStringSubstring -> StyledStringSubstring(text = text, styles = styles + style)
        is StyledStringVariable -> StyledStringVariable(name = name, styles = styles + style)
    }

fun StyledString.isBlank(): Boolean = substrings.all { it.isBlank() }

fun StyledStringLeaf.isBlank(): Boolean =
    when (this) {
        is StyledStringVariable -> false
        is StyledStringSubstring -> text.isBlank()
    }
