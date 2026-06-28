package warlockfe.warlock3.compose.components

import androidx.compose.ui.text.font.FontFamily
import kotlin.math.roundToInt

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

internal val genericFontFamilies =
    listOf(
        FontFamilyInfo("Default", FontFamily.Default),
        FontFamilyInfo("Serif", FontFamily.Serif),
        FontFamilyInfo("SansSerif", FontFamily.SansSerif),
        FontFamilyInfo("Monospace", FontFamily.Monospace),
        FontFamilyInfo("Cursive", FontFamily.Cursive),
    )

// Sample text for the font preview. ASCII only; includes digits and symbols because game text is
// full of stat numbers.
internal const val FONT_PICKER_SAMPLE_TEXT = "The quick brown fox 0123456789 +-/%"

internal const val MIN_FONT_SIZE = 6f
internal const val MAX_FONT_SIZE = 72f
internal const val FONT_SIZE_STEP = 1f

internal fun filterFontFamilies(
    families: List<FontFamilyInfo>,
    query: String,
): List<FontFamilyInfo> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return families
    return families.filter { it.familyName.contains(trimmed, ignoreCase = true) }
}

/** A compact size readout: "14" for whole numbers, otherwise one decimal ("13.5"). */
internal fun formatFontSize(size: Float): String {
    val rounded = (size * 10f).roundToInt() / 10f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}

internal expect suspend fun loadSystemFonts(): List<FontFamilyInfo>
