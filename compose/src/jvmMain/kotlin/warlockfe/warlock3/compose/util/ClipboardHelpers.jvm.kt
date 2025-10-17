package warlockfe.warlock3.compose.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.asAwtTransferable
import kotlinx.io.IOException
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
actual fun ClipEntry.getText(): String? {
    val transferable = asAwtTransferable ?: return null
    try {
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            return transferable.getTransferData(DataFlavor.stringFlavor) as String
        }
    } catch (_: IOException) {
        // ignore issues retrieving the clipboard contents.
    }
    return null
}

actual fun createClipEntry(text: String): ClipEntry {
    return ClipEntry(nativeClipEntry = StringSelection(text))
}