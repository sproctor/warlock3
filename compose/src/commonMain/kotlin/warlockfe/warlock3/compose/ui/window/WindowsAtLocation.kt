package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableListItemScope
import sh.calvin.reorderable.ReorderableListScope
import sh.calvin.reorderable.ReorderableRow
import warlockfe.warlock3.compose.components.ResizablePanel
import warlockfe.warlock3.compose.components.ResizablePanelState
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.macro.ScrollEvent
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.WindowLocation

@Composable
fun WindowsAtLocation(
    location: WindowLocation,
    defaultStyle: StyleDefinition,
    openWindows: List<String>,
    size: Int?,
    windowUiStates: List<WindowUiState>,
    horizontalPanel: Boolean,
    handleBefore: Boolean,
    selectedWindow: String,
    onSizeChanged: (Int) -> Unit,
    menuData: WarlockMenuData?,
    onActionClicked: (WarlockAction) -> Int?,
    onMoveClicked: (String, WindowLocation) -> Unit,
    onHeightChanged: (String, Int) -> Unit,
    onWidthChanged: (String, Int) -> Unit,
    onMoveWindow: (Int, Int) -> Unit,
    onCloseClicked: (String) -> Unit,
    saveStyle: (String, StyleDefinition) -> Unit,
    onWindowSelected: (String) -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    clearStream: (String) -> Unit,
) {
    if (windowUiStates.isNotEmpty()) {
        val panelState = remember(size == null) {
            ResizablePanelState(initialSize = size?.dp ?: 0.dp, minSize = 16.dp)
        }
        ResizablePanel(
            isHorizontal = horizontalPanel,
            handleBefore = handleBefore,
            state = panelState,
        ) {
            val content: @Composable ReorderableListScope.(WindowUiState, Boolean, Boolean) -> Unit =
                { uiState, isLast, isDragging ->
                    WindowViewSlot(
                        uiState = uiState,
                        location = location,
                        defaultStyle = defaultStyle,
                        openWindows = openWindows,
                        isDragging = isDragging,
                        isLast = isLast,
                        selectedWindow = selectedWindow,
                        menuData = menuData,
                        onActionClicked = onActionClicked,
                        isHorizontal = !horizontalPanel,
                        onMoveClicked = onMoveClicked,
                        onHeightChanged = onHeightChanged,
                        onWidthChanged = onWidthChanged,
                        onCloseClicked = onCloseClicked,
                        saveStyle = saveStyle,
                        onWindowSelected = onWindowSelected,
                        scrollEvents = scrollEvents,
                        handledScrollEvent = handledScrollEvent,
                        clearStream = clearStream,
                    )
                }
            if (horizontalPanel) {
                ReorderableColumn(
                    list = windowUiStates,
                    onSettle = { fromIndex, toIndex ->
                        println("from: $fromIndex, to: $toIndex")
                        onMoveWindow(fromIndex, toIndex)
                    }
                ) { index, uiState, isDragging ->
                    key(uiState.name) {
                        content(uiState, index == windowUiStates.lastIndex, isDragging)
                    }
                }
            } else {
                ReorderableRow(
                    list = windowUiStates,
                    onSettle = { fromIndex, toIndex ->
                        println("from: $fromIndex, to: $toIndex")
                        onMoveWindow(fromIndex, toIndex)
                    }
                ) { index, uiState, isDragging ->
                    key(uiState.name) {
                        content(uiState, index == windowUiStates.lastIndex, isDragging)
                    }
                }
            }
        }
        LaunchedEffect(panelState.currentSize) {
            if (size != null) {
                onSizeChanged(panelState.currentSize.value.toInt())
            }
        }
    }
}

@Composable
private fun ReorderableListScope.WindowViewSlot(
    uiState: WindowUiState,
    location: WindowLocation,
    defaultStyle: StyleDefinition,
    openWindows: List<String>,
    isDragging: Boolean,
    isLast: Boolean,
    selectedWindow: String,
    isHorizontal: Boolean,
    menuData: WarlockMenuData?,
    onActionClicked: (WarlockAction) -> Int?,
    onMoveClicked: (String, WindowLocation) -> Unit,
    onWidthChanged: (String, Int) -> Unit,
    onHeightChanged: (String, Int) -> Unit,
    onCloseClicked: (String) -> Unit,
    saveStyle: (String, StyleDefinition) -> Unit,
    onWindowSelected: (String) -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    clearStream: (String) -> Unit,
) {
    val content: @Composable ReorderableListItemScope.(Modifier) -> Unit = { modifier ->
        WindowView(
            modifier = modifier,
            headerModifier = Modifier.draggableHandle(),
            uiState = uiState,
            location = location,
            defaultStyle = defaultStyle,
            isSelected = selectedWindow == uiState.name,
            openWindows = openWindows,
            menuData = menuData,
            onActionClicked = onActionClicked,
            onMoveClicked = { onMoveClicked(uiState.name, it) },
            onCloseClicked = { onCloseClicked(uiState.name) },
            saveStyle = { saveStyle(uiState.name, it) },
            onSelected = { onWindowSelected(uiState.name) },
            scrollEvents = scrollEvents,
            handledScrollEvent = handledScrollEvent,
            clearStream = { clearStream(uiState.name) },
        )
    }

    ReorderableItem {
        if (!isLast) {
            val panelState = remember(uiState.name) {
                val size = if (isHorizontal) uiState.width else uiState.height
                ResizablePanelState(initialSize = size?.dp ?: 160.dp, minSize = 16.dp)
            }
            ResizablePanel(
                modifier = if (isHorizontal) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier.fillMaxWidth()
                },
                isHorizontal = isHorizontal,
                showHandle = !isDragging,
                state = panelState,
            ) {
                content(Modifier.matchParentSize())
            }
            LaunchedEffect(panelState.currentSize) {
                val size = panelState.currentSize.value.toInt()
                if (isHorizontal) {
                    onWidthChanged(uiState.name, size)
                } else {
                    onHeightChanged(uiState.name, size)
                }
            }
        } else {
            content(Modifier.fillMaxSize())
        }
    }
}