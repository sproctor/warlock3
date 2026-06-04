package warlockfe.warlock3.compose.components

import androidx.compose.ui.text.font.FontFamily

internal data class FontFamilyInfo(
    val familyName: String,
    val fontFamily: FontFamily,
)

data class FontUpdate(
    val size: Float?,
    val fontFamily: String?,
    val weight: Int? = null,
)

/** A selectable font weight; [weight] is the numeric value (100-900), or null for the family default. */
data class FontWeightInfo(
    val label: String,
    val weight: Int?,
)

val fontWeightOptions =
    listOf(
        FontWeightInfo("Default", null),
        FontWeightInfo("Thin", 100),
        FontWeightInfo("Extra Light", 200),
        FontWeightInfo("Light", 300),
        FontWeightInfo("Normal", 400),
        FontWeightInfo("Medium", 500),
        FontWeightInfo("Semi Bold", 600),
        FontWeightInfo("Bold", 700),
        FontWeightInfo("Extra Bold", 800),
        FontWeightInfo("Black", 900),
    )

internal expect suspend fun loadSystemFonts(): List<FontFamilyInfo>
