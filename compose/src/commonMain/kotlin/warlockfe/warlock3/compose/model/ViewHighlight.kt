package warlockfe.warlock3.compose.model

import warlockfe.warlock3.core.text.StyleDefinition

data class ViewHighlight(
    val regex: Regex,
    val styles: Map<Int, StyleDefinition>,
)