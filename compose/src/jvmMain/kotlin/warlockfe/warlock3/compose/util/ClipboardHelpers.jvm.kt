package warlockfe.warlock3.compose.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.asAwtTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
actual fun clipEntryOf(text: String): ClipEntry? = ClipEntry(StringSelection(text))

@OptIn(ExperimentalComposeUiApi::class)
actual fun ClipEntry.plainText(): String? {
    val transferable = asAwtTransferable ?: return null
    if (!transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) return null
    // The clipboard is owned by another process, so a transfer can fail after the flavour check -
    // the owner may have gone away or be refusing to serve it. A failed paste is not worth killing
    // the app over.
    return runCatching { transferable.getTransferData(DataFlavor.stringFlavor) as? String }.getOrNull()
}
