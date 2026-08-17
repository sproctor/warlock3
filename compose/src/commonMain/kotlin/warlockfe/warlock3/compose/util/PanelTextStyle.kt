package warlockfe.warlock3.compose.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.util.getIgnoringCase

/**
 * Merges a [FontConfig] over this style: each field the config leaves null keeps the style's own, so
 * a config that only sets a size inherits the family and weight. A null [font] changes nothing.
 *
 * Used for the panel font (over the platform's compact base) and again for a progress bar's per-bar
 * font override (over the resolved panel style), so the two layers compose the same way.
 */
fun TextStyle.withFont(font: FontConfig?): TextStyle =
    if (font == null) {
        this
    } else {
        copy(
            fontFamily = font.family?.let { createFontFamily(it) } ?: fontFamily,
            fontSize = font.size?.sp ?: fontSize,
            fontWeight = font.weight?.let { FontWeight(it) } ?: fontWeight,
        )
    }

/** The font a skin sets for a progress bar, read from the ambient skin. Null when it sets none. */
@Composable
fun progressBarSkinFont(skinObject: SkinObject?): FontConfig? = progressBarSkinFont(skinObject, LocalSkin.current)

/**
 * The font a skin sets for a progress bar: what the skin says for this particular bar, falling back
 * field by field to its shared `progressBar` entry, so a skin can size all its bars in one place and
 * still say something different about one of them.
 *
 * This sits above the panel font setting and below the user's own font for the bar. A skin that sizes
 * a bar has laid out the slot it sits in - the vital bars live in a fixed-height row - so the panel
 * font, which is a preference about panel text in general, should not burst it. The user asking for a
 * font on this bar in particular is the last word, over the skin included: they can see what they are
 * changing.
 *
 * Field by field, matching how [withFont] merges: a skin that sets only a size keeps the family and
 * weight it would otherwise have had.
 */
internal fun progressBarSkinFont(
    skinObject: SkinObject?,
    skin: Map<String, SkinObject>,
): FontConfig? {
    val shared = skin.getIgnoringCase("progressBar")
    return FontConfig(
        family = skinObject?.fontFamily ?: shared?.fontFamily,
        size = skinObject?.fontSize ?: shared?.fontSize,
        weight = skinObject?.fontWeight ?: shared?.fontWeight,
    ).takeUnless { it.isEmpty() }
}
