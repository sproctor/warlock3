package warlockfe.warlock3.compose.util

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.text.AnnotatedString

expect fun ClipEntry.getText(): String?

expect fun createClipEntry(text: String): ClipEntry