package cc.warlock.warlock3.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import java.awt.Cursor

@Composable
fun ResizablePanel(
    state: ResizablePanelState,
    modifier: Modifier = Modifier,
    isHorizontal: Boolean = true,
    handleBefore: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    if (isHorizontal) {
        Row(modifier.width(state.currentSize)) {
            PanelContent(
                modifier = Modifier.fillMaxHeight().weight(1f),
                state = state,
                isHorizontal = true,
                handleBefore = handleBefore,
                content = content
            )
        }
    } else {
        Column(modifier.height(state.currentSize)) {
            PanelContent(
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = state,
                isHorizontal = false,
                handleBefore = handleBefore,
                content = content
            )
        }
    }
}

@Composable
private fun PanelContent(
    modifier: Modifier,
    state: ResizablePanelState,
    isHorizontal: Boolean,
    handleBefore: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    if (handleBefore) {
        ResizablePanelHandle(isHorizontal, true, state)
    }
    Box(modifier) {
        content()
    }
    if (!handleBefore) {
        ResizablePanelHandle(isHorizontal, false, state)
    }
}

@Composable
fun ResizablePanelHandle(
    isHorizontal: Boolean,
    isBefore: Boolean,
    state: ResizablePanelState,
) {
    val modifier = Modifier
        .pointerInput(state) {
            detectDragGestures { change, _ ->
                change.consume()
                val delta = if (isHorizontal) change.position.x.toDp() else change.position.y.toDp()
                state.dispatchRawMovement(
                    if (isBefore) -delta else delta
                )
            }
        }
        .pointerHoverIcon(
            icon = PointerIcon(Cursor(if (isHorizontal) Cursor.E_RESIZE_CURSOR else Cursor.S_RESIZE_CURSOR))
        )
    val handleColor = MaterialTheme.colorScheme.onBackground
    if (isHorizontal) {
        Box(modifier.width(separatorThickness).fillMaxHeight()) {
            Spacer(
                Modifier
                    .size(width = handleThickness, height = handleSize)
                    .background(handleColor)
                    .align(Alignment.Center)
            )
        }
    } else {
        Box(
            modifier = modifier.height(separatorThickness).fillMaxWidth(),
        ) {
            Spacer(
                Modifier
                    .size(height = handleThickness, width = handleSize)
                    .background(handleColor)
                    .align(Alignment.Center)
            )
        }
    }
}

class ResizablePanelState(
    initialSize: Dp = 0.dp,
    val minSize: Dp = 0.dp,
    val maxSize: Dp = Dp.Infinity,
) {
    var currentSize by mutableStateOf(initialSize)

    fun dispatchRawMovement(delta: Dp) {
        currentSize = min(max(minSize, currentSize + delta), maxSize)
    }
}

private val separatorThickness = 4.dp
private val handleThickness = 2.dp
private val handleSize = 24.dp