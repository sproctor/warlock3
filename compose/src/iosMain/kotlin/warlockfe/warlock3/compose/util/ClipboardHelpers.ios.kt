package warlockfe.warlock3.compose.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
actual fun ClipEntry.getText(): String? {
    return getPlainText()
}

@OptIn(ExperimentalComposeUiApi::class)
actual fun createClipEntry(text: String): ClipEntry {
    return ClipEntry.withPlainText(text)
}
