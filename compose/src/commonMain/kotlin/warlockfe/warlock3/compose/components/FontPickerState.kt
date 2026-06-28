package warlockfe.warlock3.compose.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import warlockfe.warlock3.compose.util.createFontFamily
import warlockfe.warlock3.core.text.StyleDefinition

/**
 * Shared state for the font picker dialogs (desktop Jewel + mobile Material3). Owns the selected
 * family/size/weight, the family-search query, and the asynchronously loaded font list, and derives
 * the filtered list plus a combined preview style. Both platform dialogs are thin views over this.
 */
internal class FontPickerState(
    initialFamily: String,
    private val seedSize: Float,
    initialWeight: Int?,
) {
    val sizeFieldState = TextFieldState(formatFontSize(seedSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)))
    val queryState = TextFieldState()

    var selectedFamily by mutableStateOf(initialFamily)
    var weight by mutableStateOf(initialWeight)
    var familyPickerOpen by mutableStateOf(false)

    // Seeded with the generic families so the list is never empty during the async system-font load;
    // fontsLoaded is a real flag (not list-size derived) so the loading hint clears even on mobile,
    // where loadSystemFonts() legitimately returns an empty list.
    var allFamilies by mutableStateOf(genericFontFamilies)
    var fontsLoaded by mutableStateOf(false)

    /** The parsed, clamped size; invalid/empty input falls back to the seed so it is never null. */
    val effectiveSize: Float by derivedStateOf {
        sizeFieldState.text
            .toString()
            .toFloatOrNull()
            ?.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE) ?: seedSize
    }

    val filteredFamilies: List<FontFamilyInfo> by derivedStateOf {
        filterFontFamilies(allFamilies, queryState.text.toString())
    }

    /** The selected family resolved to a Compose FontFamily, for the live preview. */
    val previewFontFamily: FontFamily by derivedStateOf { createFontFamily(selectedFamily) }

    fun stepSize(delta: Float) {
        val next = (effectiveSize + delta).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        sizeFieldState.setTextAndPlaceCursorAtEnd(formatFontSize(next))
    }

    fun toFontUpdate(): FontUpdate = FontUpdate(size = effectiveSize, fontFamily = selectedFamily, weight = weight)
}

@Composable
internal fun rememberFontPickerState(
    currentStyle: StyleDefinition,
    defaultSize: Float,
): FontPickerState {
    val state =
        remember(currentStyle) {
            FontPickerState(
                initialFamily = currentStyle.fontFamily ?: "Default",
                seedSize = currentStyle.fontSize ?: defaultSize,
                initialWeight = currentStyle.fontWeight,
            )
        }
    LaunchedEffect(state) {
        state.allFamilies = genericFontFamilies + loadSystemFonts()
        state.fontsLoaded = true
    }
    return state
}
