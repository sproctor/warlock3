package warlockfe.warlock3.app.ui.window

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import warlockfe.warlock3.core.text.StyleDefinition

@Immutable
data class WindowLine(val text: AnnotatedString, val entireLineStyle: StyleDefinition?)