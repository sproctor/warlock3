package cc.warlock.warlock3.app.util

import androidx.compose.ui.text.AnnotatedString
import cc.warlock.warlock3.core.highlights.Highlight

fun AnnotatedString.highlight(highlights: List<Highlight>): AnnotatedString {
    val text = text
    return with(AnnotatedString.Builder(this)) {
        highlights.forEach { highlight ->
            val pattern = if (highlight.isRegex) {
                highlight.pattern
            } else {
                val subpattern = Regex.escape(highlight.pattern)
                if (highlight.matchPartialWord) {
                    subpattern
                } else {
                    "\\b$subpattern\\b"
                }
            }
            val regex = Regex(
                pattern = pattern,
                options = if (highlight.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet(),
            )
            regex.find(text)?.let { result ->
                addStyle(highlight.styles[0].toSpanStyle(), result.range.first, result.range.last + 1)
            }
        }
        toAnnotatedString()
    }
}