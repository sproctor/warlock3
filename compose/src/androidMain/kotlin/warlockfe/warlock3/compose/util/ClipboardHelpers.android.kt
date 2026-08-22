package warlockfe.warlock3.compose.util

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

actual fun clipEntryOf(text: String): ClipEntry? = ClipEntry(ClipData.newPlainText(null, text))

actual fun ClipEntry.plainText(): String? =
    clipData
        .takeIf { it.itemCount > 0 }
        // Reading .text rather than coercing: coerceToText needs a Context, and anything that has to
        // be coerced (a URI, an intent) is not text a command entry should receive anyway.
        ?.getItemAt(0)
        ?.text
        ?.toString()
