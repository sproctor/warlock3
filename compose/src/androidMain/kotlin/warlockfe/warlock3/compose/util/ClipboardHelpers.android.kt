package warlockfe.warlock3.compose.util

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.text.AnnotatedString

actual fun ClipEntry.getText(): String? {
    val text = StringBuilder()
    val data = clipData
    if (data.itemCount == 0)
        return null
    for (i in 0 until data.itemCount) {
        data.getItemAt(i).text?.let { text.append(it) }
    }
    return text.toString()
}

actual fun createClipEntry(text: AnnotatedString): ClipEntry {
    return ClipEntry(ClipData.newPlainText(null, text))
}
