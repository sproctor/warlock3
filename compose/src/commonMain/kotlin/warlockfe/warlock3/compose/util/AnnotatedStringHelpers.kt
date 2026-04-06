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
                                addStyle(style.toSpanStyle(), group.range_.first, group.range_.last + 1)
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
    highlightedResult: AnnotatedStringHighlightResult,
    presets: Map<String, StyleDefinition>
)

expect val MatchGroup.range_: IntRange
