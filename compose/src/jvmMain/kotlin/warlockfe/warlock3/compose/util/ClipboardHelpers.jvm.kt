package warlockfe.warlock3.compose.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.text.AnnotatedString
import java.awt.datatransfer.DataFlavor

@OptIn(ExperimentalComposeUiApi::class)
actual fun ClipEntry.getText(): String? {
    val transferable = asAwtTransferable ?: return null
    if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        return transferable.getTransferData(DataFlavor.stringFlavor) as String
    }
    return null
}

actual fun createClipEntry(text: AnnotatedString): ClipEntry {
    return ClipEntry(nativeClipEntry = text)
}