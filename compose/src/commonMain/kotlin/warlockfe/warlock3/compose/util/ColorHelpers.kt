package warlockfe.warlock3.compose.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.isUnspecified

fun WarlockColor.toColor(): Color {
    return if (isUnspecified()) Color.Unspecified else Color(argb)
}

fun Color.toWarlockColor(): WarlockColor {
    val argb: UInt = toArgb().toUInt()
    return WarlockColor(
        alpha = ((argb and 0xFF000000U) / 0x1000000U).toInt(),
        red = ((argb and 0x00FF0000U) / 0x10000U).toInt(),
        green = ((argb and 0x0000FF00U) / 0x100U).toInt(),
        blue = (argb and 0x000000FFU).toInt(),
    )
}