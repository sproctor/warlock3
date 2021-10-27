package cc.warlock.warlock3.app.model

import androidx.compose.ui.text.SpanStyle

data class ViewHighlight(
    val regex: Regex,
    val styles: List<SpanStyle?>,
)