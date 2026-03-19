package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
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
    onHeightChanged: (String, Int) -> Unit,
    onWidthChanged: (String, Int) -> Unit,
    onCloseClicked: (String) -> Unit,
    saveStyle: (String, StyleDefinition) -> Unit,
    onWindowSelected: (String) -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    clearStream: (String) -> Unit,
    dragDropState: DragDropState,
    onDrop: (DropResult) -> Unit,
) {
    // isVertical: items stack vertically (LEFT/RIGHT panels)
    val isVertical = horizontalPanel

    if (windowUiStates.isNotEmpty()) {
        val panelState = remember(size == null) {
            ResizablePanelState(initialSize = size?.dp ?: 0.dp, minSize = 16.dp)
        }
        ResizablePanel(
            isHorizontal = horizontalPanel,
            handleBefore = handleBefore,
            state = panelState,
        ) {
            DockableSection(
                location = location,
                windowUiStates = windowUiStates,
                isVertical = isVertical,
                dragDropState = dragDropState,
                onDrop = onDrop,
                defaultStyle = defaultStyle,
                openWindows = openWindows,
                selectedWindow = selectedWindow,
                menuData = menuData,
                onActionClicked = onActionClicked,

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
        LaunchedEffect(panelState.currentSize) {
            if (size != null) {
                onSizeChanged(panelState.currentSize.value.toInt())
            }
        }
    } else if (dragDropState.isDragging) {
        EmptyDropZone(
            location = location,
            isVertical = isVertical,
            dragDropState = dragDropState,
        )
    }
}

@Composable
private fun EmptyDropZone(
    location: WindowLocation,
    isVertical: Boolean,
    dragDropState: DragDropState,
) {
    val isTarget = dragDropState.dropTarget?.location == location
    val sizeModifier = if (isVertical) {
        Modifier.width(32.dp).fillMaxHeight()
    } else {
        Modifier.height(32.dp).fillMaxWidth()
    }
    Box(
        modifier = sizeModifier
            .onGloballyPositioned { coordinates ->
                dragDropState.registerSection(
                    location = location,
                    bounds = coordinates.boundsInRoot(),
                    itemBounds = emptyList(),
                    isVertical = isVertical,
                )
            }
            .then(
                if (isTarget) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                } else {
                    Modifier
                }
            )
    )

    DisposableEffect(location) {
        onDispose {
            dragDropState.unregisterSection(location)
        }
    }
}

@Composable
private fun DockableSection(
    location: WindowLocation,
    windowUiStates: List<WindowUiState>,
    isVertical: Boolean,
    dragDropState: DragDropState,
    onDrop: (DropResult) -> Unit,
    defaultStyle: StyleDefinition,
    openWindows: List<String>,
    selectedWindow: String,
    menuData: WarlockMenuData?,
    onActionClicked: (WarlockAction) -> Int?,

    onHeightChanged: (String, Int) -> Unit,
    onWidthChanged: (String, Int) -> Unit,
    onCloseClicked: (String) -> Unit,
    saveStyle: (String, StyleDefinition) -> Unit,
    onWindowSelected: (String) -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    clearStream: (String) -> Unit,
) {
    val itemBounds = remember { mutableStateListOf<Rect>() }
    val isDropTarget = dragDropState.dropTarget?.location == location
    val dropIndex = dragDropState.dropTarget?.takeIf { it.location == location }?.insertionIndex

    LaunchedEffect(windowUiStates.size) {
        while (itemBounds.size < windowUiStates.size) {
            itemBounds.add(Rect.Zero)
        }
        while (itemBounds.size > windowUiStates.size) {
            itemBounds.removeLast()
        }
    }

    val sectionModifier = Modifier
        .onGloballyPositioned { coordinates ->
            dragDropState.registerSection(
                location = location,
                bounds = coordinates.boundsInRoot(),
                itemBounds = itemBounds.toList(),
                isVertical = isVertical,
            )
        }
        .then(
            if (isDropTarget) {
                Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            } else {
                Modifier
            }
        )

    DisposableEffect(location) {
        onDispose {
            dragDropState.unregisterSection(location)
        }
    }

    val content: @Composable (Int, WindowUiState) -> Unit = { index, uiState ->
        key(uiState.name) {
            val isDraggedItem = dragDropState.isDragging &&
                    dragDropState.sourceLocation == location &&
                    dragDropState.draggedItem?.name == uiState.name

            if (dropIndex == index && dragDropState.isDragging) {
                DropIndicator(isVertical)
            }

            val itemModifier = Modifier
                .onGloballyPositioned { coordinates: LayoutCoordinates ->
                    if (index < itemBounds.size) {
                        itemBounds[index] = coordinates.boundsInRoot()
                    }
                }
                .graphicsLayer {
                    alpha = if (isDraggedItem) 0.3f else 1f
                }

            WindowViewSlot(
                modifier = itemModifier,
                uiState = uiState,
                location = location,
                index = index,
                defaultStyle = defaultStyle,
                openWindows = openWindows,
                isLast = index == windowUiStates.lastIndex,
                selectedWindow = selectedWindow,
                isHorizontal = !isVertical,
                menuData = menuData,
                onActionClicked = onActionClicked,

                onHeightChanged = onHeightChanged,
                onWidthChanged = onWidthChanged,
                onCloseClicked = onCloseClicked,
                saveStyle = saveStyle,
                onWindowSelected = onWindowSelected,
                scrollEvents = scrollEvents,
                handledScrollEvent = handledScrollEvent,
                clearStream = clearStream,
                dragDropState = dragDropState,
                onDrop = onDrop,
            )

        }
    }

    Box {
        if (isVertical) {
            Column(modifier = sectionModifier) {
                windowUiStates.forEachIndexed { index, uiState ->
                    content(index, uiState)
                }
            }
        } else {
            Row(modifier = sectionModifier) {
                windowUiStates.forEachIndexed { index, uiState ->
                    content(index, uiState)
                }
            }
        }
        if (dropIndex == windowUiStates.size && dragDropState.isDragging) {
            val alignment = if (isVertical) Alignment.BottomCenter else Alignment.CenterEnd
            DropIndicator(isVertical = isVertical, modifier = Modifier.align(alignment))
        }
    }
}

@Composable
private fun WindowViewSlot(
    modifier: Modifier,
    uiState: WindowUiState,
    location: WindowLocation,
    index: Int,
    defaultStyle: StyleDefinition,
    openWindows: List<String>,
    isLast: Boolean,
    selectedWindow: String,
    isHorizontal: Boolean,
    menuData: WarlockMenuData?,
    onActionClicked: (WarlockAction) -> Int?,
    onWidthChanged: (String, Int) -> Unit,
    onHeightChanged: (String, Int) -> Unit,
    onCloseClicked: (String) -> Unit,
    saveStyle: (String, StyleDefinition) -> Unit,
    onWindowSelected: (String) -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    clearStream: (String) -> Unit,
    dragDropState: DragDropState,
    onDrop: (DropResult) -> Unit,
) {
    val headerCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }

    val headerModifier = Modifier
        .onGloballyPositioned { headerCoordinates.value = it }
        .pointerInput(uiState.name, location, index) {
            detectDragGestures(
                onDragStart = { offset ->
                    val coords = headerCoordinates.value ?: return@detectDragGestures
                    val rootOffset = coords.localToRoot(offset)
                    dragDropState.startDrag(uiState, location, index, rootOffset)
                },
                onDrag = { change, _ ->
                    change.consume()
                    val coords = headerCoordinates.value ?: return@detectDragGestures
                    val rootOffset = coords.localToRoot(change.position)
                    dragDropState.updateDrag(rootOffset)
                },
                onDragEnd = {
                    val result = dragDropState.endDrag()
                    if (result != null) {
                        onDrop(result)
                    }
                },
                onDragCancel = {
                    dragDropState.cancelDrag()
                },
            )
        }

    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        WindowView(
            modifier = contentModifier.then(modifier),
            headerModifier = headerModifier,
            uiState = uiState,
            location = location,
            defaultStyle = defaultStyle,
            isSelected = selectedWindow == uiState.name,
            openWindows = openWindows,
            menuData = menuData,
            onActionClicked = onActionClicked,
            onCloseClicked = { onCloseClicked(uiState.name) },
            saveStyle = { saveStyle(uiState.name, it) },
            onSelected = { onWindowSelected(uiState.name) },
            scrollEvents = scrollEvents,
            handledScrollEvent = handledScrollEvent,
            clearStream = { clearStream(uiState.name) },
        )
    }

    if (!isLast) {
        val panelState = remember(uiState.name) {
            val panelSize = if (isHorizontal) uiState.width else uiState.height
            ResizablePanelState(initialSize = panelSize?.dp ?: 160.dp, minSize = 16.dp)
        }
        ResizablePanel(
            modifier = if (isHorizontal) {
                Modifier.fillMaxHeight()
            } else {
                Modifier.fillMaxWidth()
            },
            isHorizontal = isHorizontal,
            state = panelState,
        ) {
            content(Modifier.matchParentSize())
        }
        LaunchedEffect(panelState.currentSize) {
            val panelSize = panelState.currentSize.value.toInt()
            if (isHorizontal) {
                onWidthChanged(uiState.name, panelSize)
            } else {
                onHeightChanged(uiState.name, panelSize)
            }
        }
    } else {
        content(Modifier.fillMaxSize())
    }
}
