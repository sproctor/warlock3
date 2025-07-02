package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import warlockfe.warlock3.core.text.StyleDefinition
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
@Immutable
data class WindowLine(val text: AnnotatedString, val entireLineStyle: StyleDefinition?, val timestamp: Instant)