package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.AnnotatedString
import warlockfe.warlock3.core.text.StyleLayer

actual fun AnnotatedString.Builder.markLinks(
    text: AnnotatedString,
    presets: Map<String, StyleLayer>,
) {
    // TODO: implement on iOS
}

actual val MatchGroup.range_: IntRange
    get() = this.range
