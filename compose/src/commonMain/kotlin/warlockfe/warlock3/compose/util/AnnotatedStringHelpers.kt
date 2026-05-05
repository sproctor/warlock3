package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import warlockfe.warlock3.compose.model.LiteralHighlight
import warlockfe.warlock3.compose.model.RegexHighlight
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.model.isWordBoundary
import warlockfe.warlock3.core.text.StyleDefinition

fun AnnotatedString.highlight(highlights: List<ViewHighlight>): AnnotatedStringHighlightResult {
    val sourceText = this.text
    val outerSpans = this.spanStyles
    val entireLineStyles = mutableListOf<StyleDefinition>()
    val text =
        with(AnnotatedString.Builder(this)) {
            highlights.forEach { highlight ->
                when (highlight) {
                    is LiteralHighlight -> applyLiteralHighlight(highlight, sourceText, outerSpans, entireLineStyles)
                    is RegexHighlight -> applyRegexHighlight(highlight, sourceText, outerSpans, entireLineStyles)
                }
            }
            toAnnotatedString()
        }
    return AnnotatedStringHighlightResult(text, entireLineStyles)
}

private fun AnnotatedString.Builder.applyLiteralHighlight(
    highlight: LiteralHighlight,
    text: String,
    outerSpans: List<AnnotatedString.Range<SpanStyle>>,
    entireLineStyles: MutableList<StyleDefinition>,
) {
    val style = highlight.styles[0] ?: return
    val needle = highlight.literal
    if (needle.isEmpty()) return
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
