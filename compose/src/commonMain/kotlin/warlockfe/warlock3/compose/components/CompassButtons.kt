package warlockfe.warlock3.compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.arrow_right_alt
import warlockfe.warlock3.compose.generated.resources.logout
import warlockfe.warlock3.core.compass.Direction

/** Colors for [CompassButtons]: a "lit" available direction versus a dim unavailable one. */
data class CompassButtonColors(
    val litBackground: Color,
    val litBorder: Color,
    val litIcon: Color,
    val background: Color,
    val border: Color,
    val icon: Color,
)

/**
 * A grid of tappable direction buttons: the eight compass points around a central "out", with up and
 * down in their own column. Available [directions] are lit and clickable; unavailable ones are dim.
 * Clicking a lit direction invokes [onClick] with that [Direction].
 */
@Composable
fun CompassButtons(
    height: Dp,
    directions: Set<Direction>,
    onClick: (Direction) -> Unit,
    colors: CompassButtonColors,
    modifier: Modifier = Modifier,
) {
    val available = remember(directions) { directions.map { it.value.lowercase() }.toSet() }
    val gap = 3.dp
    // Icon sizes as a fixed fraction of the cell height: the rotated direction arrows and the
    // central "out" glyph.
    val arrowIconSize = height / 4
    val outIconSize = height / 5

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        // 3x3 grid of the eight compass points around a central "out".
        Column(
            modifier = Modifier.size(height),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            // Each direction is the arrow_right_alt icon (pointing east) rotated clockwise from 0deg.
            // "out" has no arrow - it renders the logout icon instead, see below - hence the null.
            val rows: List<List<Pair<String, Float?>>> =
                listOf(
                    listOf("nw" to 225f, "n" to 270f, "ne" to 315f),
                    listOf("w" to 180f, "out" to null, "e" to 0f),
                    listOf("sw" to 135f, "s" to 90f, "se" to 45f),
                )
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    row.forEach { (value, rotation) ->
                        CompassCell(
                            modifier = Modifier.fillMaxHeight().weight(1f),
                            lit = available.contains(value),
                            colors = colors,
                            onClick = { onClick(Direction(value)) },
                        ) { contentColor ->
                            if (rotation == null) {
                                // "Out" of the room reads as a door with an arrow leaving it
                                // (Material Symbols "logout").
                                Image(
                                    modifier = Modifier.size(outIconSize),
                                    painter = painterResource(Res.drawable.logout),
                                    colorFilter = ColorFilter.tint(contentColor),
                                    contentDescription = "out",
                                )
                            } else {
                                DirectionArrow(
                                    rotationDegrees = rotation,
                                    size = arrowIconSize,
                                    color = contentColor,
                                    contentDescription = value,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Up / down sit in their own column, as in the design.
        Column(
            modifier = Modifier.height(height).width(height * 0.36f),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            CompassCell(
                modifier = Modifier.fillMaxWidth().weight(1f),
                lit = available.contains("up"),
                colors = colors,
                onClick = { onClick(Direction("up")) },
            ) { contentColor ->
                DirectionArrow(
                    rotationDegrees = 270f,
                    size = arrowIconSize,
                    color = contentColor,
                    contentDescription = "up",
                )
            }
            CompassCell(
                modifier = Modifier.fillMaxWidth().weight(1f),
                lit = available.contains("down"),
                colors = colors,
                onClick = { onClick(Direction("down")) },
            ) { contentColor ->
                DirectionArrow(
                    rotationDegrees = 90f,
                    size = arrowIconSize,
                    color = contentColor,
                    contentDescription = "down",
                )
            }
        }
    }
}

@Composable
private fun CompassCell(
    lit: Boolean,
    colors: CompassButtonColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (contentColor: Color) -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(if (lit) colors.litBackground else colors.background)
                .border(
                    width = Dp.Hairline,
                    color = if (lit) colors.litBorder else colors.border,
                    shape = shape,
                ).then(if (lit) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content(if (lit) colors.litIcon else colors.icon)
    }
}

/** A compass direction arrow: the east-pointing [arrow_right_alt] rotated [rotationDegrees] clockwise. */
@Composable
private fun DirectionArrow(
    rotationDegrees: Float,
    size: Dp,
    color: Color,
    contentDescription: String,
) {
    Image(
        modifier = Modifier.size(size).rotate(rotationDegrees),
        painter = painterResource(Res.drawable.arrow_right_alt),
        colorFilter = ColorFilter.tint(color),
        contentDescription = contentDescription,
    )
}
