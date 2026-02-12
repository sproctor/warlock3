package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD

actual fun Key.getLabel(): String {
    return KeyEvent.getKeyText(nativeKeyCode)
}

actual val Key.Companion.NumPadDotFix: Key
    get() = Key(KeyEvent.VK_DECIMAL, KEY_LOCATION_NUMPAD)
