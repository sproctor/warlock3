package cc.warlock.warlock3.core.util

import cc.warlock.warlock3.core.text.WarlockColor

fun String.toWarlockColor(): WarlockColor? {
    return if (get(0) == '#') {
        if (length >= 7) {
            WarlockColor(
                red = substring(1, 3).toIntOrNull(16) ?: return null,
                green = substring(3, 5).toIntOrNull(16) ?: return null,
                blue = substring(5, 7).toIntOrNull(16) ?: return null,
            )
        } else {
            null
        }
    } else if (this == "default") {
        WarlockColor.Unspecified
    } else {
        null
    }
}