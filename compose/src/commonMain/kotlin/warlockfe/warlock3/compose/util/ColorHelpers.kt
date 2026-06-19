package warlockfe.warlock3.compose.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.isUnspecified

/**
 * The background relative-luminance at which black and white foregrounds give equal WCAG contrast:
 * `sqrt(1.05 * 0.05) - 0.05`. Below it a light foreground reads better; above it a dark one does.
 * Use this (rather than a naive 0.5 midpoint) when classifying a [Color] as light or dark by its
 * [luminance][androidx.compose.ui.graphics.luminance].
 */
internal const val CONTRAST_CROSSOVER_LUMINANCE = 0.17912878f

fun WarlockColor?.toColor(default: Color = Color.Unspecified): Color =
    if (this == null || isUnspecified()) {
        default
    } else {
        Color(argb)
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

fun Color.Companion.parseHexOrNull(text: String): Color? {
    val trimmed =
        text
            .trim()
            .removePrefix("#")
            .takeIf { it.length == 6 || it.length == 8 }

    val argb =
        trimmed
            ?.toULongOrNull(16)
            ?.let { if (trimmed.length == 6) 0xFF000000UL or it else it }
            ?: return null
    return Color(argb.toLong())
}
