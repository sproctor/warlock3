package cc.warlock.warlock3.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import cc.warlock.warlock3.core.text.WarlockColor

fun Color.toWarlockColor(): WarlockColor {
    val argb = toArgb()
    return WarlockColor(red = (argb and 0x00FF0000) / 0x10000, blue = argb and 0x000000FF, green = (argb and 0x0000FF00) / 0x100)
}