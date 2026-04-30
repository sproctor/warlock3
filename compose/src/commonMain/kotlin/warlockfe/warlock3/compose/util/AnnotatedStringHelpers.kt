package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.AnnotatedString
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.core.text.StyleDefinition

fun AnnotatedString.highlight(highlights: List<ViewHighlight>): AnnotatedStringHighlightResult {
    val entireLineStyles = mutableListOf<StyleDefinition>()
    val text = with(AnnotatedString.Builder(this)) {
        highlights.forEach { highlight ->
            highlight.regex.findAll(text).forEach { result ->
                for ((index, group) in result.groups.withIndex()) {
                    if (group != null) {
                        highlight.styles[index]?.let { style ->
                            if (style.entireLine) {
                                entireLineStyles.add(style)
                                addStyle(style.toSpanStyle(), 0, length)
                            } else {
                                val matchStart = group.range_.first
                                val matchEnd = group.range_.last + 1
                                val spanStyle = style.toSpanStyle()
                                addStyle(spanStyle, matchStart, matchEnd)
                                // Compose's span merge picks the most recently started active
                                // style, so a wider highlight that started before a span would
                                // lose to the span's color. Re-apply the highlight on each
                                // overlapping span range so it starts at (or after) the link.
                                spanStyles.forEach { linkRange ->
                                    val overlapStart = maxOf(linkRange.start, matchStart)
                                    val overlapEnd = minOf(linkRange.end, matchEnd)
                                    if (overlapStart < overlapEnd) {
                                        addStyle(spanStyle, overlapStart, overlapEnd)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        toAnnotatedString()
    }
    return AnnotatedStringHighlightResult(text, entireLineStyles)
}

data class AnnotatedStringHighlightResult(
    val text: AnnotatedString,
    val entireLineStyles: List<StyleDefinition>,
)

expect fun AnnotatedString.Builder.markLinks(
    text: AnnotatedString,
    presets: Map<String, StyleDefinition>
)

expect val MatchGroup.range_: IntRange
