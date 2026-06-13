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
    // Precomputed once per highlight (not per line): the lowercased maximal runs of word characters in
    // the literal. A whole-word literal can only occur in a line if every one of these tokens is itself a
    // word token of that line, so highlight()'s pre-filter skips the per-line scan whenever the line is
    // missing any of them. This covers multi-word literals ("greater orc"), not just single words. Empty
    // for literals made entirely of non-word characters (e.g. ">"), which always fall through to the scan.
    val wordTokens: Set<String> = wordTokensOf(literal)

    // The token a HighlightIndex files this whole-word literal under: the longest of its word tokens, since
    // longer tokens occur in fewer lines and so produce the fewest false candidates. Null when the literal
    // has no word tokens (e.g. ">"); such literals can't be token-excluded and are checked against every line.
    val probeToken: String? = wordTokens.maxByOrNull { it.length }

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

// The lowercased maximal runs of word characters in [text]. highlight() compares a literal's tokens
// against a line's tokens to pre-filter whole-word highlights, so both must tokenize identically.
internal fun wordTokensOf(text: String): Set<String> {
    val tokens = HashSet<String>()
    var start = -1
    for (i in text.indices) {
        if (isWordChar(text[i])) {
            if (start < 0) start = i
        } else if (start >= 0) {
            tokens.add(text.substring(start, i).lowercase())
            start = -1
        }
    }
    if (start >= 0) tokens.add(text.substring(start).lowercase())
    return tokens
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
