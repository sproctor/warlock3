package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import warlockfe.warlock3.compose.model.LiteralHighlight
import warlockfe.warlock3.compose.model.RegexHighlight
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.model.isWordBoundary
import warlockfe.warlock3.compose.model.isWordChar
import warlockfe.warlock3.core.text.StyleDefinition

fun AnnotatedString.highlight(highlights: List<ViewHighlight>): AnnotatedStringHighlightResult {
    val sourceText = this.text
    val outerSpans = this.spanStyles
    val entireLineStyles = mutableListOf<StyleDefinition>()
    // Cheap pre-filter for the common, hot case: a power user can have hundreds of single-word,
    // whole-word literal highlights, almost none of which match a given line. Build the set of the
    // line's lowercased word tokens once, so those highlights become an O(1) membership check instead
    // of a per-highlight (case-insensitive) scan of the whole line.
    val lineWords = wordTokens(sourceText)
    val text =
        with(AnnotatedString.Builder(this)) {
            highlights.forEach { highlight ->
                when (highlight) {
                    is LiteralHighlight -> applyLiteralHighlight(highlight, sourceText, lineWords, outerSpans, entireLineStyles)
                    is RegexHighlight -> applyRegexHighlight(highlight, sourceText, outerSpans, entireLineStyles)
                }
            }
            toAnnotatedString()
        }
    return AnnotatedStringHighlightResult(text, entireLineStyles)
}

// The lowercased maximal runs of word characters in [text], used to pre-filter whole-word highlights.
private fun wordTokens(text: String): Set<String> {
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

private fun AnnotatedString.Builder.applyLiteralHighlight(
    highlight: LiteralHighlight,
    text: String,
    lineWords: Set<String>,
    outerSpans: List<AnnotatedString.Range<SpanStyle>>,
    entireLineStyles: MutableList<StyleDefinition>,
) {
    val style = highlight.style ?: return
    val needle = highlight.literal
    // A whole-word literal can only match if it is itself a single word token present in the line; an
    // O(1) check (against precomputed, allocation-free fields) that lets us skip the scan for the
    // overwhelmingly common non-matching highlights. The scan below still confirms the actual match
    // (incl. case for case-sensitive highlights).
    if (!highlight.matchPartialWord && highlight.isSingleWord && highlight.loweredLiteral !in lineWords) return
    val needleLen = needle.length
    var idx = text.indexOf(needle, startIndex = 0, ignoreCase = highlight.ignoreCase)
    while (idx >= 0) {
        val end = idx + needleLen
        if (highlight.matchPartialWord || isWordBoundary(text, idx, end)) {
            applyStyleAtRange(style, idx, end, outerSpans, entireLineStyles)
            idx = text.indexOf(needle, startIndex = end, ignoreCase = highlight.ignoreCase)
        } else {
            idx = text.indexOf(needle, startIndex = idx + 1, ignoreCase = highlight.ignoreCase)
        }
    }
}

private fun AnnotatedString.Builder.applyRegexHighlight(
    highlight: RegexHighlight,
    text: String,
    outerSpans: List<AnnotatedString.Range<SpanStyle>>,
    entireLineStyles: MutableList<StyleDefinition>,
) {
    highlight.regex.findAll(text).forEach { result ->
        for ((index, group) in result.groups.withIndex()) {
            if (group != null) {
                highlight.styles[index]?.let { style ->
                    val matchStart = group.range_.first
                    val matchEnd = group.range_.last + 1
                    applyStyleAtRange(style, matchStart, matchEnd, outerSpans, entireLineStyles)
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.applyStyleAtRange(
    style: StyleDefinition,
    matchStart: Int,
    matchEnd: Int,
    outerSpans: List<AnnotatedString.Range<SpanStyle>>,
    entireLineStyles: MutableList<StyleDefinition>,
) {
    if (style.entireLine) {
        entireLineStyles.add(style)
        addStyle(style.toSpanStyle(), 0, length)
    } else {
        val spanStyle = style.toSpanStyle()
        addStyle(spanStyle, matchStart, matchEnd)
        // Compose's span merge picks the most recently started active
        // style, so a wider highlight that started before a span would
        // lose to the span's color. Re-apply the highlight on each
        // overlapping span range so it starts at (or after) the link.
        outerSpans.forEach { linkRange ->
            val overlapStart = maxOf(linkRange.start, matchStart)
            val overlapEnd = minOf(linkRange.end, matchEnd)
            if (overlapStart < overlapEnd) {
                addStyle(spanStyle, overlapStart, overlapEnd)
            }
        }
    }
}

data class AnnotatedStringHighlightResult(
    val text: AnnotatedString,
    val entireLineStyles: List<StyleDefinition>,
)

expect fun AnnotatedString.Builder.markLinks(
    text: AnnotatedString,
    presets: Map<String, StyleDefinition>,
)

expect val MatchGroup.range_: IntRange
