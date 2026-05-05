package warlockfe.warlock3.compose.model

import warlockfe.warlock3.core.text.StyleDefinition

sealed interface ViewHighlight {
    val styles: Map<Int, StyleDefinition>
    val sound: String?

    fun containsMatchIn(text: String): Boolean
}

data class LiteralHighlight(
    val literal: String,
    val matchPartialWord: Boolean,
    val ignoreCase: Boolean,
    override val styles: Map<Int, StyleDefinition>,
    override val sound: String?,
) : ViewHighlight {
    override fun containsMatchIn(text: String): Boolean {
        if (literal.isEmpty()) return false
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
    override val styles: Map<Int, StyleDefinition>,
    override val sound: String?,
) : ViewHighlight {
    override fun containsMatchIn(text: String): Boolean = regex.containsMatchIn(text)
}

internal fun isWordBoundary(text: CharSequence, start: Int, end: Int): Boolean =
    isAtBoundary(text, start) && isAtBoundary(text, end)

private fun isAtBoundary(text: CharSequence, position: Int): Boolean =
    when (position) {
        0 -> text.isNotEmpty() && isWordChar(text[0])
        text.length -> text.isNotEmpty() && isWordChar(text[text.length - 1])
        else -> isWordChar(text[position - 1]) != isWordChar(text[position])
    }

internal fun isWordChar(c: Char): Boolean =
    (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '_'
