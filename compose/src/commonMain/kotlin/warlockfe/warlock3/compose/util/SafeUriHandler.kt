package warlockfe.warlock3.compose.util

import androidx.compose.ui.platform.UriHandler
import co.touchlab.kermit.Logger

class SafeUriHandler(
    private val delegate: UriHandler,
) : UriHandler {
    private val logger = Logger.withTag("SafeUriHandler")

    override fun openUri(uri: String) {
        try {
            delegate.openUri(uri)
        } catch (e: Exception) {
            // Desktop.browse() failed — likely no default browser registered (macOS -10814),
            // or the URI scheme is unrecognized or sandboxed. Log and swallow.
            logger.w { "Failed to open URI: $uri — ${e.message}" }
        }
    }
}
