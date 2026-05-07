package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import warlockfe.warlock3.compose.desktop.ui.window.DesktopDragOverlay
import warlockfe.warlock3.compose.desktop.ui.window.DesktopWindowsAtLocation
import warlockfe.warlock3.compose.ui.window.DragDropState
import warlockfe.warlock3.compose.ui.window.DropResult
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.compose.ui.window.WindowView
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.macro.ScrollEvent
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.WindowLocation

@Composable
fun DesktopGameTextWindows(
    topWindowUiStates: List<WindowUiState>,
    bottomWindowUiStates: List<WindowUiState>,
    leftWindowUiStates: List<WindowUiState>,
    rightWindowUiStates: List<WindowUiState>,
    mainWindowUiState: WindowUiState?,
    defaultStyle: StyleDefinition,
    selectedWindow: String,
    openWindows: List<String>,
    topHeight: Int?,
    bottomHeight: Int?,
    leftWidth: Int?,
    rightWidth: Int?,
    menuData: WarlockMenuData?,
    onActionClick: (WarlockAction) -> Int?,
    onHeightChange: (String, Int) -> Unit,
    onWidthChange: (String, Int) -> Unit,
    onSizeChange: (WindowLocation, Int) -> Unit,
    onDrop: (DropResult) -> Unit,
    onCloseClick: (String) -> Unit,
    saveStyle: (String, StyleDefinition) -> Unit,
    onWindowSelect: (String) -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    modifier: Modifier = Modifier,
    clearStream: (String) -> Unit,
) {
    val dragDropState = remember { DragDropState() }

    Box(modifier = modifier) {
        Row(modifier = Modifier.fillMaxSize()) {
            DesktopWindowsAtLocation(
                location = WindowLocation.LEFT,
                size = leftWidth,
                windowUiStates = leftWindowUiStates,
                defaultStyle = defaultStyle,
                openWindows = openWindows,
                horizontalPanel = true,
                handleBefore = false,
                selectedWindow = selectedWindow,
                onSizeChange = { onSizeChange(WindowLocation.LEFT, it) },
                menuData = menuData,
                onActionClick = onActionClick,
                onHeightChange = onHeightChange,
                onWidthChange = onWidthChange,
                onCloseClick = onCloseClick,
                saveStyle = saveStyle,
                onWindowSelect = onWindowSelect,
                scrollEvents = scrollEvents,
                handledScrollEvent = handledScrollEvent,
                clearStream = clearStream,
                dragDropState = dragDropState,
                onDrop = onDrop,
            )
            Column(modifier = Modifier.weight(1f)) {
                DesktopWindowsAtLocation(
                    location = WindowLocation.TOP,
                    size = topHeight,
                    windowUiStates = topWindowUiStates,
                    defaultStyle = defaultStyle,
                    openWindows = openWindows,
                    horizontalPanel = false,
                    handleBefore = false,
                    selectedWindow = selectedWindow,
                    onSizeChange = { onSizeChange(WindowLocation.TOP, it) },
                    menuData = menuData,
                    onActionClick = onActionClick,
                    onHeightChange = onHeightChange,
                    onWidthChange = onWidthChange,
                    onCloseClick = onCloseClick,
                    saveStyle = saveStyle,
                    onWindowSelect = onWindowSelect,
                    scrollEvents = scrollEvents,
                    handledScrollEvent = handledScrollEvent,
                    clearStream = clearStream,
                    dragDropState = dragDropState,
                    onDrop = onDrop,
                )
                if (mainWindowUiState != null) {
                    // Main text pane (WindowView) is still M3 in commonMain — fall through.
                    // Migrated in step 8.
                    WindowView(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        headerModifier = Modifier,
                        uiState = mainWindowUiState,
                        location = WindowLocation.MAIN,
                        defaultStyle = defaultStyle,
                        isSelected = selectedWindow == mainWindowUiState.name,
                        openWindows = openWindows,
                        menuData = menuData,
                        onActionClick = onActionClick,
                        onCloseClick = {},
                        saveStyle = {
                            saveStyle(mainWindowUiState.name, it)
                        },
                        onSelect = { onWindowSelect(mainWindowUiState.name) },
                        scrollEvents = scrollEvents,
                        handledScrollEvent = handledScrollEvent,
                        clearStream = { clearStream(mainWindowUiState.name) },
                    )
                }
                DesktopWindowsAtLocation(
                    location = WindowLocation.BOTTOM,
                    size = bottomHeight,
                    windowUiStates = bottomWindowUiStates,
                    defaultStyle = defaultStyle,
                    openWindows = openWindows,
                    horizontalPanel = false,
                    handleBefore = true,
                    selectedWindow = selectedWindow,
                    onSizeChange = { onSizeChange(WindowLocation.BOTTOM, it) },
                    menuData = menuData,
                    onActionClick = onActionClick,
                    onHeightChange = onHeightChange,
                    onWidthChange = onWidthChange,
                    onCloseClick = onCloseClick,
                    saveStyle = saveStyle,
                    onWindowSelect = onWindowSelect,
                    scrollEvents = scrollEvents,
                    handledScrollEvent = handledScrollEvent,
                    clearStream = clearStream,
                    dragDropState = dragDropState,
                    onDrop = onDrop,
                )
            }
            DesktopWindowsAtLocation(
                location = WindowLocation.RIGHT,
                size = rightWidth,
                windowUiStates = rightWindowUiStates,
                defaultStyle = defaultStyle,
                openWindows = openWindows,
                horizontalPanel = true,
                handleBefore = true,
                selectedWindow = selectedWindow,
                onSizeChange = { onSizeChange(WindowLocation.RIGHT, it) },
                menuData = menuData,
                onActionClick = onActionClick,
                onHeightChange = onHeightChange,
                onWidthChange = onWidthChange,
                onCloseClick = onCloseClick,
                saveStyle = saveStyle,
                onWindowSelect = onWindowSelect,
                scrollEvents = scrollEvents,
                handledScrollEvent = handledScrollEvent,
                clearStream = clearStream,
                dragDropState = dragDropState,
                onDrop = onDrop,
            )
        }
        DesktopDragOverlay(dragDropState = dragDropState)
    }
}
