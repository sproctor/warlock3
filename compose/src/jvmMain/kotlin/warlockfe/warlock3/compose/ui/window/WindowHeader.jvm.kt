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
import java.awt.Cursor
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.drag_indicator
import warlockfe.warlock3.core.window.WindowLocation

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
                                JewelTheme.globalColors.borders.normal.copy(alpha = 0.4f),
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
                    colorFilter = ColorFilter.tint(
                        JewelTheme.globalColors.text.normal.copy(alpha = 0.5f),
                    ),
                    contentDescription = "Drag to re-arrange window",
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(modifier = Modifier.weight(1f)) {
                title()
            }
        }
    }
}
