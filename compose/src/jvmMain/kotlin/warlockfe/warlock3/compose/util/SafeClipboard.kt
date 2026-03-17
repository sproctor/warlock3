package warlockfe.warlock3.compose.util

// desktopMain
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class SafeClipboard(private val delegate: Clipboard) : Clipboard by delegate {
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        try {
            delegate.setClipEntry(clipEntry)
        } catch (_: IllegalStateException) {
            // Windows clipboard temporarily locked by another process — swallow silently
        }
    }
}

@Composable
fun rememberSafeClipboard(): Clipboard {
    val clipboard = LocalClipboard.current
    return remember(clipboard) { SafeClipboard(clipboard) }
}