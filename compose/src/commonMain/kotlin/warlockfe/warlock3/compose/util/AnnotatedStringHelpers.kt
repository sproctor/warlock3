package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.AnnotatedString
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.core.text.StyleDefinition

fun AnnotatedString.highlight(highlights: List<ViewHighlight>): AnnotatedStringHighlightResult {
    val entireLineStyles = mutableListOf<StyleDefinition>()
    val text = with(AnnotatedString.Builder(this)) {
        highlights.forEach { highlight ->
            highlight.regex.find(text)?.let { result ->
                for ((index, group) in result.groups.withIndex()) {
                    if (group != null) {
                        highlight.styles[index]?.let { style ->
                            addStyle(style.toSpanStyle(), group.range.first, group.range.last + 1)
                            if (style.entireLine) {
                                entireLineStyles.add(style)
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
    val entireLineStyles: List<StyleDefinition>
)