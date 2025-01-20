package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily

@OptIn(ExperimentalTextApi::class)
actual fun createFontFamily(familyName: String): FontFamily {
    return FontFamily(familyName)
}