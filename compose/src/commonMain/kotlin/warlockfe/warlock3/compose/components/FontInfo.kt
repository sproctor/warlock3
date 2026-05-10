package warlockfe.warlock3.compose.components

import androidx.compose.ui.text.font.FontFamily

internal data class FontFamilyInfo(
    val familyName: String,
    val fontFamily: FontFamily,
)

data class FontUpdate(
    val size: Float?,
    val fontFamily: String?,
)

internal expect suspend fun loadSystemFonts(): List<FontFamilyInfo>
