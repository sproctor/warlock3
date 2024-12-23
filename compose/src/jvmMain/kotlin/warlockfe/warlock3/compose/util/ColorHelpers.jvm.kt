package warlockfe.warlock3.compose.util

import androidx.compose.ui.graphics.Color

fun Color.toAwtColor(): java.awt.Color {
    return java.awt.Color(red, green, blue, alpha)
}
