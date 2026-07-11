package warlockfe.warlock3.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 * panel surface, which would misreport legibility. Two distinct boundaries: an outer hairline separating
 * the chip from the panel, and (only when the background is explicitly set) an inset ring separating the
 * fill from the window, so a background set to the window's own color is still visible as a background.
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
    // The inset ring marks an explicitly-set background (Fill or None); an inherited-unset one has none.
    val hasExplicitBackground = resolved.background != Background.Unset
    // Luminance-derived so it reads on any skin: dark ring on light fills, light ring on dark fills.
    val ring = if (field.luminance() > 0.5f) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.3f)
    val textColor = resolved.textColor.toColor(default = if (field.luminance() > 0.5f) Color.Black else Color.White)

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                // Outer hairline: chip <-> panel. Panel-agnostic neutral so it reads on light and dark themes.
                .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .background(field),
        contentAlignment = Alignment.Center,
    ) {
        if (hasExplicitBackground) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(2.dp)
                    .border(1.dp, ring, RoundedCornerShape(3.dp)),
            )
        }
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
