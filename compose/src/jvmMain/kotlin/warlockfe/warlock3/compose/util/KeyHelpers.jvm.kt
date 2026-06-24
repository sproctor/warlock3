package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.nativeKeyLocation
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD

// AWT reports these keys with the same nativeKeyCode for their numpad and non-numpad variants, so
// the key location is all that distinguishes them and getKeyText() returns identical text. Prefix
// "NumPad " on the numpad ones so their labels don't collide.
private val numPadPrefixKeys =
    listOf(
        // Punctuation/Enter whose numpad variant reuses the main key's code.
        KeyEvent.VK_COMMA,
        KeyEvent.VK_ENTER,
        KeyEvent.VK_EQUALS,
        // Editing/navigation keys: with Num Lock off the numpad emits these secondary functions at
        // KEY_LOCATION_NUMPAD, sharing the codes of the dedicated editing/arrow cluster.
        KeyEvent.VK_INSERT,
        KeyEvent.VK_DELETE,
        KeyEvent.VK_HOME,
        KeyEvent.VK_END,
        KeyEvent.VK_PAGE_UP,
        KeyEvent.VK_PAGE_DOWN,
        KeyEvent.VK_UP,
        KeyEvent.VK_DOWN,
        KeyEvent.VK_LEFT,
        KeyEvent.VK_RIGHT,
    )

actual fun Key.getLabel(): String =
    (if (nativeKeyLocation == KEY_LOCATION_NUMPAD && numPadPrefixKeys.contains(nativeKeyCode)) "NumPad " else "") +
        KeyEvent.getKeyText(nativeKeyCode)

actual val Key.Companion.NumPadDotFix: Key
    get() = Key(KeyEvent.VK_DECIMAL, KEY_LOCATION_NUMPAD)
