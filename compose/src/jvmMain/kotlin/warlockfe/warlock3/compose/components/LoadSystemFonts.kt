package warlockfe.warlock3.compose.components

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.skia.FontMgr

@OptIn(ExperimentalTextApi::class)
internal actual fun loadSystemFonts(): List<FontFamilyInfo> {
    val fontManager = FontMgr.default
    return (0 until fontManager.familiesCount)
        .map { index -> fontManager.getFamilyName(index) }
        .sorted()
        .map { familyName ->
            FontFamilyInfo(
                familyName = familyName,
                fontFamily = FontFamily(familyName),
            )
        }
}
