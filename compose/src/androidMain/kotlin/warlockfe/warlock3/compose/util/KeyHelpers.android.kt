package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode

actual fun Key.getLabel(): String {
    val event = android.view.KeyEvent(0, this.nativeKeyCode)
    return event.displayLabel.toString()
}

actual val Key.Companion.NumPadDotFix: Key
    get() = Key.NumPadDot
