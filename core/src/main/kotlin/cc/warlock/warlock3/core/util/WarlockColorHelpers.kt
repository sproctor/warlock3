package cc.warlock.warlock3.core.util

import cc.warlock.warlock3.core.text.WarlockColor

fun String.toWarlockColor(): WarlockColor? {
    return if (get(0) == '#') {
        drop(1).toLongOrNull(16)?.let { argb ->
            WarlockColor(argb = if (length <= 7) argb + 0xFF000000L else argb)
        }
    } else if (this == "default") {
        WarlockColor.Unspecified
    } else {
        null
    }
}