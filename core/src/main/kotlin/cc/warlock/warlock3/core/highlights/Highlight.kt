package cc.warlock.warlock3.core.highlights

import cc.warlock.warlock3.core.text.StyleDefinition

data class Highlight(
    val pattern: String,
    val styles: List<StyleDefinition>,
    val isRegex: Boolean,
    val matchPartialWord: Boolean,
    val ignoreCase: Boolean,
)
