package warlockfe.warlock3.compose.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
actual fun clipEntryOf(text: String): ClipEntry? = ClipEntry.withPlainText(text)

@OptIn(ExperimentalComposeUiApi::class)
actual fun ClipEntry.plainText(): String? = if (hasPlainText()) getPlainText() else null
