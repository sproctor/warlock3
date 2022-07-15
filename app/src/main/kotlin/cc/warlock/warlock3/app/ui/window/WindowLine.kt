package cc.warlock.warlock3.app.ui.window

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import cc.warlock.warlock3.core.text.StyleDefinition

@Immutable
data class WindowLine(val text: AnnotatedString, val serialNumber: Long, val entireLineStyle: StyleDefinition?)