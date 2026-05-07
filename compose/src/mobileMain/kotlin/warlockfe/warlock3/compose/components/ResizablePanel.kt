package warlockfe.warlock3.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun ResizablePanel(
    state: ResizablePanelState,
    modifier: Modifier = Modifier,
    isHorizontal: Boolean = true,
    handleBefore: Boolean = false,
    showHandle: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    if (isHorizontal) {
        Row(modifier.width(state.currentSize)) {
            PanelContent(
                modifier = Modifier.fillMaxHeight().weight(1f),
                state = state,
                isHorizontal = true,
                handleBefore = handleBefore,
                showHandle = showHandle,
                content = content,
            )
        }
    } else {
        Column(modifier.height(state.currentSize)) {
            PanelContent(
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = state,
                isHorizontal = false,
                handleBefore = handleBefore,
                showHandle = showHandle,
                content = content,
            )
        }
    }
}

@Composable
private fun PanelContent(
    state: ResizablePanelState,
    isHorizontal: Boolean,
    handleBefore: Boolean,
    showHandle: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (handleBefore && showHandle) {
        ResizablePanelHandle(isHorizontal, true, state)
    }
    Box(modifier) {
        content()
    }
    if (!handleBefore && showHandle) {
        ResizablePanelHandle(isHorizontal, false, state)
    }
}

@Composable
fun ResizablePanelHandle(
    isHorizontal: Boolean,
    isBefore: Boolean,
    state: ResizablePanelState,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val density = LocalDensity.current
    val handleModifier =
        modifier
            .pointerHoverIcon(
                icon = getResizeCursor(isHorizontal),
            ).hoverable(interactionSource = interactionSource)
            .focusable(interactionSource = interactionSource)
            .draggable(
                interactionSource = interactionSource,
                orientation = if (isHorizontal) Orientation.Horizontal else Orientation.Vertical,
                state =
                    rememberDraggableState { delta ->
                        val deltaDp = with(density) { delta.toDp() }
                        state.dispatchRawMovement(
                            if (isBefore) -deltaDp else deltaDp,
                        )
                    },
            )
    val handleColor =
        if (isDragging) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.outline
        }
    val handleShape = CircleShape
    val handleThickness = 2.dp
    val handleSize = if (isDragging) 40.dp else 36.dp
    val separatorThickness = 4.dp
    val boxModifier =
        if (isHorizontal) {
            handleModifier.width(separatorThickness).fillMaxHeight()
        } else {
            handleModifier.height(separatorThickness).fillMaxWidth()
        }
    Box(boxModifier) {
        val sizeModifier =
            if (isHorizontal) {
                Modifier.size(width = handleThickness, height = handleSize).align(Alignment.Center)
            } else {
                Modifier.size(height = handleThickness, width = handleSize).align(Alignment.Center)
            }
        Spacer(
            sizeModifier
                .background(
                    color = handleColor,
                    shape = handleShape,
                ),
        )
        if (isHovered) {
            Spacer(
                sizeModifier
                    .background(
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.08f),
                        shape = handleShape,
                    ),
            )
        }
        if (isFocused) {
            Spacer(
                sizeModifier
                    .background(
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.1f),
                        shape = handleShape,
                    ),
            )
        }
    }
}
