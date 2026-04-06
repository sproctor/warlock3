package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.AnnotatedString
import co.touchlab.kermit.Logger
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.core.text.StyleDefinition

private val logger = Logger.withTag("highlight")

fun AnnotatedString.highlight(highlights: List<ViewHighlight>): AnnotatedStringHighlightResult {
    val entireLineStyles = mutableListOf<StyleDefinition>()
    val text = with(AnnotatedString.Builder(this)) {
        highlights.forEach { highlight ->
            highlight.regex.findAll(text).forEach { result ->
                logger.d { "highlight \"${highlight.regex.pattern}\" matched ${result.value}" }
                for ((index, group) in result.groups.withIndex()) {
                    if (group != null) {
                        highlight.styles[index]?.let { style ->
                            if (style.entireLine) {
                                entireLineStyles.add(style)
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
