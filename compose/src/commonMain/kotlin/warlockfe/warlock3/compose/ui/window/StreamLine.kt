package warlockfe.warlock3.compose.ui.window

import androidx.compose.ui.text.AnnotatedString
import warlockfe.warlock3.core.text.StyleDefinition
import kotlin.time.ExperimentalTime

sealed interface StreamLine {
    val serialNumber: Long
}

@OptIn(ExperimentalTime::class)
data class StreamTextLine(
    val text: AnnotatedString?,
    val entireLineStyle: StyleDefinition?,
    override val serialNumber: Long,
) : StreamLine

data class StreamImageLine(
    val url: String,
    override val serialNumber: Long,
) : StreamLine
