package warlockfe.warlock3.compose.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

actual val LocalScrollbarStyle: ProvidableCompositionLocal<ScrollbarStyle>
    get() = androidx.compose.foundation.LocalScrollbarStyle

actual typealias ScrollbarStyle = androidx.compose.foundation.ScrollbarStyle

actual fun ScrollbarStyle(unhoverColor: Color, hoverColor: Color): ScrollbarStyle {
    return androidx.compose.foundation.ScrollbarStyle(
        minimalHeight = 16.dp,
        thickness = 8.dp,
        shape = RoundedCornerShape(4.dp),
        hoverDurationMillis = 300,
        unhoverColor = unhoverColor,
        hoverColor = hoverColor,
    )
}