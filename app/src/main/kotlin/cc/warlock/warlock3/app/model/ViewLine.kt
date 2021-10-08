package cc.warlock.warlock3.app.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import cc.warlock.warlock3.core.StyledString

data class ViewLine(
    val backgroundColor: Color?,
    val stringFactory: (Map<String, StyledString>) -> AnnotatedString,
)