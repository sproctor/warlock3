package warlockfe.warlock3.compose.components

import androidx.compose.ui.text.font.FontFamily
import warlockfe.warlock3.core.text.FontConfig
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

/** The font this picker result represents, or null when nothing is set (a "reset to default"). */
fun FontUpdate.toFontConfig(): FontConfig? = FontConfig(family = fontFamily, size = size, weight = weight).takeUnless { it.isEmpty() }

/** A short human label for a font selector button, e.g. "Menlo 13", "Helvetica", or "Default". */
fun FontConfig?.fontLabel(): String {
    if (this == null || isEmpty()) return "Default"
    val fam = family ?: "Default"
    val pointSize = size
    return if (pointSize != null) "$fam ${formatFontSize(pointSize)}" else fam
}

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

// The generic families offered when the picker is restricted to monospace fonts: only the two that
// are (or can be) fixed-pitch.
internal val monospaceGenericFontFamilies =
    listOf(
        FontFamilyInfo("Default", FontFamily.Default),
        FontFamilyInfo("Monospace", FontFamily.Monospace),
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

/**
 * Loads the installed system font families. When [monospaceOnly] is true, only fixed-pitch families
 * are returned (used by the monospace font picker). Only Desktop enumerates system fonts; Android and
 * iOS return an empty list and fall back to the generic families.
 */
internal expect suspend fun loadSystemFonts(monospaceOnly: Boolean): List<FontFamilyInfo>
