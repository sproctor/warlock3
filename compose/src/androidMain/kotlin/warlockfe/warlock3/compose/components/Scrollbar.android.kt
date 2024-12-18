package warlockfe.warlock3.compose.components

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

actual val LocalScrollbarStyle: ProvidableCompositionLocal<ScrollbarStyle>
    get() = staticCompositionLocalOf { ScrollbarStyle() }

actual class ScrollbarStyle

actual fun ScrollbarStyle(unhoverColor: Color, hoverColor: Color): ScrollbarStyle {
    return ScrollbarStyle()
}
