package warlockfe.warlock3.compose.model

import warlockfe.warlock3.core.prefs.models.IgnoreMatchMode

/**
 * A compiled ignore pattern, matched against a window line's rendered text. A line matching any ignore
 * is hidden from the window view (display-only: it stays in the buffer and still reaches scripts and
 * logs, so removing the ignore restores it).
 */
sealed interface ViewIgnore {
    fun matches(text: String): Boolean
}

data class LiteralIgnore(
    val literal: String,
    val mode: IgnoreMatchMode,
    val ignoreCase: Boolean,
) : ViewIgnore {
    override fun matches(text: String): Boolean =
        when (mode) {
            IgnoreMatchMode.LINE -> text.equals(literal, ignoreCase = ignoreCase)
            IgnoreMatchMode.WORD -> containsWholeWord(text, literal, ignoreCase)
            IgnoreMatchMode.CONTAINS -> text.contains(literal, ignoreCase = ignoreCase)
        }
}

data class RegexIgnore(
    val regex: Regex,
) : ViewIgnore {
    override fun matches(text: String): Boolean = regex.containsMatchIn(text)
}
