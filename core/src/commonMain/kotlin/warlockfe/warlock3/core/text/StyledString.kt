package warlockfe.warlock3.core.text

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

data class StyledString(
    val substrings: PersistentList<StyledStringLeaf> = persistentListOf(),
) {
    constructor(text: String, styles: List<WarlockStyle> = emptyList()) :
        this(persistentListOf(StyledStringSubstring(text, styles.toImmutableList())))

    constructor(text: String, style: WarlockStyle) : this(text, listOf(style))

    // addingAll on a persistent list appends in O(elements added) with structural sharing, so folding
    // many fragments into one line (e.g. a room description) is O(total) rather than the O(n²) of
    // repeated (a + b) whole-list copies.
    operator fun plus(string: StyledString): StyledString = copy(substrings = substrings.addingAll(string.substrings))

    fun applyStyle(style: WarlockStyle): StyledString =
        copy(
            substrings =
                substrings.mutate { leaves ->
                    for (i in leaves.indices) {
                        leaves[i] = leaves[i].applyStyle(style)
                    }
                },
        )

    /** The plain text, with unresolved component/variable references rendered as %name%; e.g. for logging or matching. */
    fun toText(): String {
        val builder = StringBuilder()
        substrings.forEach { substring ->
            when (substring) {
                is StyledStringSubstring -> builder.append(substring.text)
                is StyledStringVariable -> builder.append("%${substring.name}%")
            }
        }
        return builder.toString()
    }
}

sealed interface StyledStringLeaf {
    val styles: ImmutableList<WarlockStyle>
}

data class StyledStringSubstring(
    val text: String,
    override val styles: ImmutableList<WarlockStyle>,
) : StyledStringLeaf

data class StyledStringVariable(
    val name: String,
    override val styles: ImmutableList<WarlockStyle>,
) : StyledStringLeaf

fun StyledStringLeaf.applyStyle(style: WarlockStyle): StyledStringLeaf =
    when (this) {
        is StyledStringSubstring -> copy(styles = (styles + style).toImmutableList())
        is StyledStringVariable -> copy(styles = (styles + style).toImmutableList())
    }

fun StyledString.isBlank(): Boolean = substrings.all { it.isBlank() }

fun StyledStringLeaf.isBlank(): Boolean =
    when (this) {
        is StyledStringVariable -> false
        is StyledStringSubstring -> text.isBlank()
    }
