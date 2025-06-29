package warlockfe.warlock3.compose.util

import android.util.Log
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import io.github.oshai.kotlinlogging.KotlinLogging

actual fun Key.getLabel(): String {
    val event = android.view.KeyEvent(0, this.nativeKeyCode)
    Log.d("key", "key: ${event.displayLabel}")
    KotlinLogging.logger { "key: $this" }
    return "??"
}