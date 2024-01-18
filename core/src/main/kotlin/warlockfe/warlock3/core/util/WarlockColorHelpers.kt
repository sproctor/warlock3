package warlockfe.warlock3.core.util

import warlockfe.warlock3.core.text.WarlockColor

fun String.toWarlockColor(): WarlockColor? {
    return when {
        isEmpty() -> null
        startsWith('#') -> {
            drop(1).toLongOrNull(16)?.let { argb ->
                WarlockColor(argb = if (length <= 7) argb + 0xFF000000L else argb)
            }
        }
        this == "default" -> WarlockColor.Unspecified
        else -> null
    }
}