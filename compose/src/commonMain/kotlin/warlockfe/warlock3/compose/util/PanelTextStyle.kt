package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import warlockfe.warlock3.core.text.FontConfig

/**
 * Merges a [FontConfig] over this style: each field the config leaves null keeps the style's own, so
 * a config that only sets a size inherits the family and weight. A null [font] changes nothing.
 *
 * Used for the panel font (over the platform's compact base) and again for a progress bar's per-bar
 * font override (over the resolved panel style), so the two layers compose the same way.
 */
fun TextStyle.withFont(font: FontConfig?): TextStyle =
    if (font == null) {
        this
    } else {
        copy(
            fontFamily = font.family?.let { createFontFamily(it) } ?: fontFamily,
            fontSize = font.size?.sp ?: fontSize,
            fontWeight = font.weight?.let { FontWeight(it) } ?: fontWeight,
        )
    }
