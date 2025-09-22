package warlockfe.warlock3.compose.components

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FontMgr

@OptIn(ExperimentalTextApi::class)
internal actual suspend fun loadSystemFonts(): List<FontFamilyInfo> {
    return withContext(Dispatchers.IO) {
        val fontManager = FontMgr.default
        (0 until fontManager.familiesCount)
            .map { index -> fontManager.getFamilyName(index) }
            .sorted()
            .map { familyName ->
                FontFamilyInfo(
                    familyName = familyName,
                    fontFamily = FontFamily(familyName),
                )
            }
    }
}
