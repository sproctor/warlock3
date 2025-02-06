package warlockfe.warlock3.compose.util

import java.awt.Desktop
import java.net.URL

actual fun openUrl(url: URL) {
    try {
        Desktop.getDesktop().browse(url.toURI())
    } catch (_: Exception) {
        // Silently ignore problems
    }
}