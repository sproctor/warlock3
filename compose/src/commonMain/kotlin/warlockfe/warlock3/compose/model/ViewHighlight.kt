package warlockfe.warlock3.compose.model

import androidx.compose.ui.text.SpanStyle

data class ViewHighlight(
    val regex: Regex,
    val styles: Map<Int, SpanStyle>,
)