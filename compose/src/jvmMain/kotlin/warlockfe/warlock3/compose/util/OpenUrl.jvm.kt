package warlockfe.warlock3.compose.util

import com.eygraber.uri.Uri
import com.eygraber.uri.toURI
import java.awt.Desktop

actual fun openUrl(url: Uri) {
    try {
        Desktop.getDesktop().browse(url.toURI())
    } catch (_: Exception) {
        // Silently ignore problems
    }
}
