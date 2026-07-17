package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import warlockfe.warlock3.compose.model.LiteralHighlight
import warlockfe.warlock3.compose.model.RegexHighlight
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.model.isWordBoundary
import warlockfe.warlock3.compose.model.wordTokensOf
import warlockfe.warlock3.compose.ui.settings.toStyleDefinition
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.resolve

fun AnnotatedString.highlight(
    index: HighlightIndex,
    monoFont: FontConfig? = null,
): AnnotatedStringHighlightResult {
    val sourceText = this.text
    val outerSpans = this.spanStyles
    val entireLineStyles = mutableListOf<StyleDefinition>()
    // The set of the line's lowercased word tokens, computed once. The index uses it to pick out the
    // highlights that could possibly match, and applyLiteralHighlight reuses it as a per-highlight filter.
    val lineWords = wordTokensOf(sourceText)
    val text =
        with(AnnotatedString.Builder(this)) {
            index.candidatesFor(lineWords).forEach { highlight ->
                when (highlight) {
                    is LiteralHighlight -> applyLiteralHighlight(highlight, sourceText, lineWords, outerSpans, entireLineStyles, monoFont)
                    is RegexHighlight -> applyRegexHighlight(highlight, sourceText, outerSpans, entireLineStyles, monoFont)
                }
            }
            toAnnotatedString()
        }
    return AnnotatedStringHighlightResult(text, entireLineStyles)
}

// Convenience for tests, benchmarks, and other non-hot-path callers: builds a throwaway index for a single
// call. Per-line production callers should build a [HighlightIndex] once per highlight-list change (see
// HighlightIndex) and reuse it, so the O(highlights) index build stays off the per-line path.
fun AnnotatedString.highlight(highlights: List<ViewHighlight>): AnnotatedStringHighlightResult = highlight(HighlightIndex(highlights))

/**
 * A reusable index over a highlight list that lets [highlight] skip the highlights that cannot match a
 * given line. A power user can have hundreds of highlights, almost none of which match any single line;
 * checking each one per line is O(highlights). Building this index once per highlight-list change turns
 * the per-line cost into O(line words + actual candidates).
 *
 * A whole-word literal can only occur in a line if every one of its word tokens is itself a word token of
 * that line, so each such highlight is filed under one representative "probe" token (its longest token,
 * which occurs in the fewest lines); a line need only consider highlights whose probe token it contains.
 * Highlights that cannot be excluded this way (regexes, partial-word literals, and literals made entirely
 * of non-word characters) are checked against every line.
 */
class HighlightIndex(
    val highlights: List<ViewHighlight>,
) {
    private class Indexed(
        val order: Int,
        val highlight: ViewHighlight,
    )

    // Checked against every line.
    private val unindexed = ArrayList<Indexed>()

    // Whole-word literals filed under their probe token.
    private val byProbeToken = HashMap<String, MutableList<Indexed>>()

    init {
        highlights.forEachIndexed { order, highlight ->
            val probe = (highlight as? LiteralHighlight)?.takeIf { !it.matchPartialWord }?.probeToken
            val entry = Indexed(order, highlight)
            if (probe == null) {
                unindexed.add(entry)
            } else {
                byProbeToken.getOrPut(probe) { ArrayList() }.add(entry)
            }
        }
    }

    // The highlights that could match a line with these word tokens, in original (configured) order so
    // overlapping highlights resolve exactly as they would if the whole list were applied. Excluded
    // highlights are whole-word literals whose probe token is absent from the line, which therefore
    // cannot match and would add no styling.
    internal fun candidatesFor(lineWords: Set<String>): List<ViewHighlight> {
        val matched = ArrayList(unindexed)
        if (byProbeToken.isNotEmpty()) {
            for (word in lineWords) {
                byProbeToken[word]?.let { matched.addAll(it) }
            }
        }
        matched.sortBy { it.order }
        return matched.map { it.highlight }
    }

    // For diagnostics: how many highlights a line with these word tokens is actually checked against
    // (the rest are excluded by probe token). Lean — no list/sort allocation, since slow-line logging
    // calls it on the hot path's behalf.
    internal fun candidateCount(lineWords: Set<String>): Int {
        var count = unindexed.size
        if (byProbeToken.isNotEmpty()) {
            for (word in lineWords) count += byProbeToken[word]?.size ?: 0
        }
        return count
    }
}

private fun AnnotatedString.Builder.applyLiteralHighlight(
    highlight: LiteralHighlight,
    text: String,
    lineWords: Set<String>,
    outerSpans: List<AnnotatedString.Range<SpanStyle>>,
    entireLineStyles: MutableList<StyleDefinition>,
    monoFont: FontConfig?,
) {
    val style = highlight.style ?: return
    val needle = highlight.literal
    // A whole-word literal can only occur in this line if every one of its word tokens is also a word
    // token of the line, so skip the scan when the line is missing any of them. This catches the common
    // non-matching highlights (often hundreds per line, including multi-word ones like "greater orc") with
    // an O(tokens) membership check instead of a case-insensitive scan of the whole line. The scan below
    // still confirms the actual match (adjacency, boundaries, and case for case-sensitive highlights).
    if (!highlight.matchPartialWord && highlight.wordTokens.isNotEmpty() && !lineWords.containsAll(highlight.wordTokens)) return
    val needleLen = needle.length
    var idx = text.indexOf(needle, startIndex = 0, ignoreCase = highlight.ignoreCase)
    while (idx >= 0) {
        val end = idx + needleLen
        if (highlight.matchPartialWord || isWordBoundary(text, idx, end)) {
            applyStyleAtRange(style, idx, end, outerSpans, entireLineStyles, monoFont)
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
    monoFont: FontConfig?,
) {
    highlight.regex.findAll(text).forEach { result ->
        for ((index, group) in result.groups.withIndex()) {
            if (group != null) {
                highlight.styles[index]?.let { style ->
                    val matchStart = group.range_.first
                    val matchEnd = group.range_.last + 1
                    applyStyleAtRange(style, matchStart, matchEnd, outerSpans, entireLineStyles, monoFont)
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.applyStyleAtRange(
    style: StyleLayer,
    matchStart: Int,
    matchEnd: Int,
    outerSpans: List<AnnotatedString.Range<SpanStyle>>,
    entireLineStyles: MutableList<StyleDefinition>,
    monoFont: FontConfig?,
) {
    if (style.entireLine == true) {
        entireLineStyles.add(style.toStyleDefinition())
        addStyle(resolve(listOf(style)).toSpanStyle(monoFont), 0, length)
    } else {
        val spanStyle = resolve(listOf(style)).toSpanStyle(monoFont)
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
    presets: Map<String, StyleLayer>,
)

expect val MatchGroup.range_: IntRange
