package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.font.FontFamily

actual fun createFontFamily(familyName: String): FontFamily {
    return when (familyName) {
        "Serif" -> FontFamily.Serif
        "SansSerif" -> FontFamily.SansSerif
        "Monospace" -> FontFamily.Monospace
        "Cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }
}
