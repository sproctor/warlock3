package warlockfe.warlock3.compose.components

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.graphics.Color

expect val LocalScrollbarStyle: ProvidableCompositionLocal<ScrollbarStyle>

expect class ScrollbarStyle

expect fun ScrollbarStyle(
    unhoverColor: Color,
    hoverColor: Color,
): ScrollbarStyle