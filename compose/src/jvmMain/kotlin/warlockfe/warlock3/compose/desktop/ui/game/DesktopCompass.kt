package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.components.CompassView
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.arrow_right_alt
import warlockfe.warlock3.compose.generated.resources.check
import warlockfe.warlock3.compose.generated.resources.logout
import warlockfe.warlock3.core.compass.Direction
import warlockfe.warlock3.core.prefs.CompassStyle

/**
 * The compass. Renders either a grid of tonal direction buttons (the default) or the classic skin
 * compass rose ([CompassView]); right-clicking opens a small menu (anchored above the compass) to
 * switch styles, with the active [style] marked by a check icon. Persisting the choice is the
 * caller's responsibility (via [onStyleChange]). Available exits are lit; unavailable directions
 * are dark. Clicking a lit direction sends that movement command.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DesktopCompass(
    height: Dp,
    directions: Set<Direction>,
    onClick: (Direction) -> Unit,
    style: CompassStyle,
    onStyleChange: (CompassStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier =
            modifier.onClick(
                matcher = PointerMatcher.mouse(PointerButton.Secondary),
                onClick = { menuOpen = true },
            ),
    ) {
        when (style) {
            CompassStyle.BUTTONS -> {
                CompassButtons(
                    height = height,
                    directions = directions,
                    onClick = onClick,
                )
            }

            CompassStyle.ROSE -> {
                CompassView(
                    height = height,
                    directions = directions,
                    onClick = onClick,
                )
            }
        }

        if (menuOpen) {
            CompassStyleMenu(
                style = style,
                onDismiss = { menuOpen = false },
                onSelect = {
                    onStyleChange(it)
                    menuOpen = false
                },
            )
        }
    }
}

/** Right-click menu anchored above the compass; the active style carries a check icon. */
@Composable
private fun CompassStyleMenu(
    style: CompassStyle,
    onDismiss: () -> Unit,
    onSelect: (CompassStyle) -> Unit,
) {
    val positionProvider =
        remember {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    val x =
                        (anchorBounds.right - popupContentSize.width)
                            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                    val y = (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
                    return IntOffset(x, y)
                }
            }
        }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val shape = RoundedCornerShape(6.dp)
        Column(
            modifier =
                Modifier
                    .width(190.dp)
                    .background(gameChrome.panel, shape)
                    .border(Dp.Hairline, gameChrome.border, shape)
                    .padding(4.dp),
        ) {
            CompassStyleMenuItem("Compass buttons", style == CompassStyle.BUTTONS) {
                onSelect(CompassStyle.BUTTONS)
            }
            CompassStyleMenuItem("Compass rose", style == CompassStyle.ROSE) {
                onSelect(CompassStyle.ROSE)
            }
        }
    }
}

@Composable
private fun CompassStyleMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fixed leading slot keeps both labels aligned whether or not the check is shown.
        Box(modifier = Modifier.size(16.dp)) {
            if (selected) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    painter = painterResource(Res.drawable.check),
                    colorFilter = ColorFilter.tint(gameChrome.accentSubtle),
                    contentDescription = "selected",
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(text = label, color = gameChrome.textPrimary)
    }
}

@Composable
private fun CompassButtons(
    height: Dp,
    directions: Set<Direction>,
    onClick: (Direction) -> Unit,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (contentColor: Color) -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(
                    if (lit) gameChrome.litBackground else gameChrome.panelAlt,
                ).border(
                    width = Dp.Hairline,
                    color = if (lit) gameChrome.litBorder else gameChrome.border,
                    shape = shape,
                ).then(if (lit) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content(if (lit) gameChrome.litIcon else gameChrome.compassDarkIcon)
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
