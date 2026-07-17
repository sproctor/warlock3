package warlockfe.warlock3.core.text

import kotlin.math.pow

/** The 0..255 channels packed into [WarlockColor.argb], alpha ignored (previews composite opaquely). */
private fun WarlockColor.channel(shift: Int): Int = ((argb shr shift) and 0xFF).toInt()

/**
 * WCAG relative luminance (0 = black, 1 = white) of an opaque color, used for contrast checks and to pick
 * a legible ring/border tint. Unspecified colors have no luminance of their own, so callers should
 * resolve them against a real background first.
 */
fun WarlockColor.relativeLuminance(): Double {
    fun linear(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(channel(16)) + 0.7152 * linear(channel(8)) + 0.0722 * linear(channel(0))
}

/**
 * The WCAG contrast ratio between two opaque colors, from 1:1 (identical) to 21:1 (black on white).
 * Symmetric. Text is generally readable at 4.5:1 and this app warns below ~3:1 (see the appearance
 * editor); deliberately low-contrast text is a valid choice, so this only informs, never blocks.
 */
fun contrastRatio(
    a: WarlockColor,
    b: WarlockColor,
): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}
