package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import java.awt.event.KeyEvent

actual fun Key.getLabel(): String {
    return KeyEvent.getKeyText(nativeKeyCode)
}
