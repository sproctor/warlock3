package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily

@OptIn(ExperimentalTextApi::class)
actual fun createFontFamily(familyName: String): FontFamily {
    return when (familyName) {
        "Default" -> FontFamily.Default
        "Serif" -> FontFamily.Serif
        "SansSerif" -> FontFamily.SansSerif
        "Monospace" -> FontFamily.Monospace
        "Cursive" -> FontFamily.Cursive
        else -> FontFamily(familyName)
    }
}
