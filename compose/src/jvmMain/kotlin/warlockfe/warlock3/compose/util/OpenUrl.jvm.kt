package warlockfe.warlock3.compose.util

import java.awt.Desktop
import java.net.URI
import java.net.URL

actual fun openUrl(url: URI) {
    try {
        Desktop.getDesktop().browse(url)
    } catch (_: Exception) {
        // Silently ignore problems
    }
}