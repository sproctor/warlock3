package warlockfe.warlock3.compose.util

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key

@OptIn(InternalComposeUiApi::class)
actual fun Key.getLabel(): String {
    return "??"
}