package warlockfe.warlock3.compose.model

import warlockfe.warlock3.core.text.StyleDefinition

sealed interface ViewHighlight {
    val sound: String?

    fun containsMatchIn(text: String): Boolean
}

data class LiteralHighlight(
    val literal: String,
    val matchPartialWord: Boolean,
    val ignoreCase: Boolean,
    val style: StyleDefinition?,
    override val sound: String?,
) : ViewHighlight {
    // Precomputed once per highlight (not per line): highlight()'s pre-filter checks whole-word literals
    // against the line's word-token set, so it needs the lowercased literal and whether the literal is a
    // single word token. Computing these here keeps that check allocation-free on the per-line hot path.
    val loweredLiteral: String = literal.lowercase()
    val isSingleWord: Boolean = literal.isNotEmpty() && literal.all { isWordChar(it) }

    override fun containsMatchIn(text: String): Boolean {
        if (matchPartialWord) return text.contains(literal, ignoreCase = ignoreCase)
        var idx = text.indexOf(literal, startIndex = 0, ignoreCase = ignoreCase)
        while (idx >= 0) {
            if (isWordBoundary(text, idx, idx + literal.length)) return true
            idx = text.indexOf(literal, startIndex = idx + 1, ignoreCase = ignoreCase)
        }
        return false
    }
}

data class RegexHighlight(
    val regex: Regex,
    val styles: Map<Int, StyleDefinition>,
    override val sound: String?,
) : ViewHighlight {
    override fun containsMatchIn(text: String): Boolean = regex.containsMatchIn(text)
}

internal fun isWordBoundary(
    text: CharSequence,
    start: Int,
    end: Int,
): Boolean = isAtBoundary(text, start) && isAtBoundary(text, end)

private fun isAtBoundary(
    text: CharSequence,
    position: Int,
): Boolean =
    when (position) {
        0 -> text.isNotEmpty() && isWordChar(text[0])
        text.length -> text.isNotEmpty() && isWordChar(text[text.length - 1])
        else -> isWordChar(text[position - 1]) != isWordChar(text[position])
    }

internal fun isWordChar(c: Char): Boolean = (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '_'
