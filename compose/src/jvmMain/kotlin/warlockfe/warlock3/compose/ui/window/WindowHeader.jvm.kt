package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.ui.game.WarlockGameChrome
import warlockfe.warlock3.compose.desktop.ui.game.WindowMenuButton
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.drag_indicator
import warlockfe.warlock3.core.window.WindowLocation
import java.awt.Cursor

private val moveCursor = PointerIcon(Cursor(Cursor.MOVE_CURSOR))

@Composable
actual fun WindowHeader(
    title: @Composable () -> Unit,
    location: WindowLocation,
    isSelected: Boolean,
    onSettingsClick: () -> Unit,
    onClearClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDraggable = location != WindowLocation.MAIN
    ContextMenuArea(
        items = {
            buildList {
                add(
                    ContextMenuItem(
                        label = "Window Settings ...",
                        onClick = onSettingsClick,
                    ),
                )
                add(
                    ContextMenuItem(
                        label = "Clear window",
                        onClick = onClearClick,
                    ),
                )
                if (location != WindowLocation.MAIN) {
                    add(
                        ContextMenuItem(
                            label = "Hide window",
                            onClick = onCloseClick,
                        ),
                    )
                }
            }
        },
    ) {
        Row(
            modifier =
                modifier
                    .then(
                        if (isDraggable) {
                            Modifier
                                .pointerHoverIcon(moveCursor)
                                .hoverable(interactionSource)
                        } else {
                            Modifier
                        },
                    ).then(
                        if (isDraggable && isHovered && !isSelected) {
                            Modifier.background(
                                WarlockGameChrome.border.copy(alpha = 0.5f),
                            )
                        } else {
                            Modifier
                        },
                    ).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isDraggable) {
                Image(
                    modifier = Modifier.size(16.dp),
                    painter = painterResource(Res.drawable.drag_indicator),
                    colorFilter =
                        ColorFilter.tint(
                            if (isSelected) {
                                WarlockGameChrome.accentSubtle
                            } else {
                                WarlockGameChrome.textFaint
                            },
                        ),
                    contentDescription = "Drag to re-arrange window",
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(modifier = Modifier.weight(1f)) {
                title()
            }
            // The same actions as the right-click context menu, surfaced as a visible "..." button.
            WindowMenuButton(
                tint = if (isSelected) WarlockGameChrome.accentSubtle else WarlockGameChrome.textFaint,
                horizontalAlignment = Alignment.End,
            ) {
                selectableItem(selected = false, onClick = onSettingsClick) {
                    Text("Window settings ...")
                }
                selectableItem(selected = false, onClick = onClearClick) {
                    Text("Clear window")
                }
                if (location != WindowLocation.MAIN) {
                    selectableItem(selected = false, onClick = onCloseClick) {
                        Text("Hide window")
                    }
                }
            }
        }
    }
}
