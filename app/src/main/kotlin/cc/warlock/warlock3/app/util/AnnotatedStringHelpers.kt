package cc.warlock.warlock3.app.util

import androidx.compose.ui.text.AnnotatedString
import cc.warlock.warlock3.core.highlights.Highlight

fun AnnotatedString.highlight(highlights: List<Highlight>): AnnotatedString {
    val text = text
    return with(AnnotatedString.Builder(this)) {
        highlights.forEach { highlight ->
            val index = text.indexOf(highlight.pattern)
            if (index >= 0) {
                addStyle(highlight.styles[0].toSpanStyle(), index, index + highlight.pattern.length)
            }
        }
        toAnnotatedString()
    }
}