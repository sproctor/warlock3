package warlockfe.warlock3.app.util

import androidx.compose.ui.text.AnnotatedString
import warlockfe.warlock3.app.model.ViewHighlight

fun AnnotatedString.highlight(highlights: List<ViewHighlight>): AnnotatedString {
    val text = text // FIXME: is this useful?
    return with(AnnotatedString.Builder(this)) {
        highlights.forEach { highlight ->
            highlight.regex.find(text)?.let { result ->
                for ((index, group) in result.groups.withIndex()) {
                    if (group != null) {
                        highlight.styles[index]?.let { style ->
                            addStyle(style, group.range.first, group.range.last + 1)
                        }
                    }
                }
            }
        }
        toAnnotatedString()
    }
}