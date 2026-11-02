package warlockfe.warlock3.compose.util

import com.eygraber.uri.Uri

// java.awt.Desktop would start the AWT toolkit, which a Tao app never loads, so hand the URL to
// the platform's own opener instead. Those openers launch anything they are given, and this URL
// comes from the game server, so only web links get that far.
actual fun openUrl(url: Uri) {
    val scheme = url.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return
    val os = System.getProperty("os.name").lowercase()
    val command =
        when {
            os.contains("mac") -> listOf("open", url.toString())
            os.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", url.toString())
            else -> listOf("xdg-open", url.toString())
        }
    try {
        ProcessBuilder(command).inheritIO().start()
    } catch (_: Exception) {
        // Silently ignore problems
    }
}
