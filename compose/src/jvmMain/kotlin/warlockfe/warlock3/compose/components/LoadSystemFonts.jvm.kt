package warlockfe.warlock3.compose.components

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

@OptIn(ExperimentalTextApi::class)
internal actual suspend fun loadSystemFonts(monospaceOnly: Boolean): List<FontFamilyInfo> =
    withContext(Dispatchers.IO) {
        val fontManager = FontMgr.default
        (0 until fontManager.familiesCount)
            .map { index -> fontManager.getFamilyName(index) }
            .let { families ->
                if (monospaceOnly) {
                    // Skia's Typeface knows whether a family is fixed-pitch; keep only those.
                    families.filter { familyName ->
                        fontManager.matchFamilyStyle(familyName, FontStyle.NORMAL)?.isFixedPitch == true
                    }
                } else {
                    families
                }
            }.sorted()
            .map { familyName ->
                FontFamilyInfo(
                    familyName = familyName,
                    fontFamily = FontFamily(familyName),
                )
            }
    }
