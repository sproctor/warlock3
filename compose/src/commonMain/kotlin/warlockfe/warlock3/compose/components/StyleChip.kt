package warlockfe.warlock3.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.Background
import warlockfe.warlock3.core.text.ResolvedStyle
import warlockfe.warlock3.core.text.toHexString

/**
 * An honest miniature of how a style renders **in game**: a fixed-width swatch that composites the
 * resolved style over the effective game window background ([windowBackground]) - never the settings
 * panel surface, which would misreport legibility. A single hairline separates the chip from the panel;
 * whether the background is explicitly set or inherited is reported by the row's text label, not by the
 * chip's own border.
 *
 * The chip is deliberately allowed to be unreadable - that is real information. The item's *name* is
 * rendered elsewhere in normal UI color and stays legible; this shows only the style. Callers pass the
 * already-resolved style and window background; the chip never walks the cascade itself.
 */
@Composable
fun StyleChip(
    resolved: ResolvedStyle,
    windowBackground: Color,
    // Fixed swatch size by default so list rows align into a swatch column; the editor's larger preview
    // overrides this with a full-width, taller sample.
    modifier: Modifier = Modifier.width(72.dp).height(28.dp),
    sampleText: String = "Aa",
) {
    // Fill = the set color; None/unset both show the window background (None is transparent over it).
    val field =
        when (val bg = resolved.background) {
            is Background.Fill -> bg.color.toColor(default = windowBackground)
            Background.None -> windowBackground
            Background.Unset -> windowBackground
        }
    val textColor = resolved.textColor.toColor(default = if (field.luminance() > 0.5f) Color.Black else Color.White)

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                // Hairline: chip <-> panel. Panel-agnostic neutral so it reads on light and dark themes.
                .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .background(field),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = sampleText,
            style =
                TextStyle(
                    color = textColor,
                    fontWeight = resolved.weight?.let { FontWeight(it) },
                    fontStyle = if (resolved.italic) FontStyle.Italic else null,
                    textDecoration = if (resolved.underline) TextDecoration.Underline else null,
                    fontSize = 13.sp,
                ),
        )
    }
}

/** The trailing background label for a list row: `no bg` (inherit), `none` (transparent), or the hex fill. */
fun backgroundLabel(background: Background): String =
    when (background) {
        is Background.Fill -> background.color.toHexString() ?: "#??????"
        Background.None -> "none"
        Background.Unset -> "no bg"
    }

/** The "Inherit" option's annotation in the background picker: what the background falls back to. */
fun inheritedBackgroundLabel(inherited: Background): String =
    when (inherited) {
        is Background.Fill -> "inherits ${inherited.color.toHexString() ?: "a color"}"
        Background.None -> "inherits none (transparent)"
        Background.Unset -> "inherits the window background"
    }
