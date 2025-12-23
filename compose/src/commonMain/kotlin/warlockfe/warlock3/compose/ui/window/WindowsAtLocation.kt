package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableListItemScope
import sh.calvin.reorderable.ReorderableListScope
import sh.calvin.reorderable.ReorderableRow
import warlockfe.warlock3.compose.components.ResizablePanel
import warlockfe.warlock3.compose.components.ResizablePanelState
import warlockfe.warlock3.compose.util.SettingsContextMenuItemKey
import warlockfe.warlock3.compose.util.addItem
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.WindowLocation

@Composable
fun WindowsAtLocation(
    location: WindowLocation,
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
    val windows = windowUiStates.filter { it.window.location == location }.sortedBy { it.window.position }
    if (windows.isNotEmpty()) {
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
                    list = windows,
                    onSettle = onMoveWindow
                ) { index, uiState, isDragging ->
                    key(uiState.name) {
                        content(uiState, index == windows.lastIndex, isDragging)
                    }
                }
            } else {
                ReorderableRow(
                    list = windows,
                    onSettle = onMoveWindow,
                ) { index, uiState, isDragging ->
                    key(uiState.name) {
                        content(uiState, index == windows.lastIndex, isDragging)
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
    var showSettingsDialog by remember { mutableStateOf(false) }
    val content: @Composable ReorderableListItemScope.(Modifier) -> Unit = { modifier ->
        WindowView(
            modifier = modifier,
            headerModifier = Modifier.draggableHandle(),
            uiState = uiState,
            isSelected = selectedWindow == uiState.name,
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
                val size = if (isHorizontal) uiState.window.width else uiState.window.height
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