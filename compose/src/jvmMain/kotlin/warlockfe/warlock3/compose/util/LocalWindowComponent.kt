package warlockfe.warlock3.compose.util

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.awt.ComposeWindow

val LocalWindowComponent = staticCompositionLocalOf<ComposeWindow> {
    error("LocalWindowComponent not provided")
}