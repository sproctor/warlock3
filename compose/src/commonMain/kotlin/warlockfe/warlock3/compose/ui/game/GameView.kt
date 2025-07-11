package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.components.CompassView
import warlockfe.warlock3.compose.components.ResizablePanel
import warlockfe.warlock3.compose.components.ResizablePanelState
import warlockfe.warlock3.compose.icons.Arrow_right
import warlockfe.warlock3.compose.ui.components.DialogContent
import warlockfe.warlock3.compose.ui.components.HandsView
import warlockfe.warlock3.compose.ui.components.IndicatorView
import warlockfe.warlock3.compose.ui.window.ScrollEvent
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.compose.ui.window.WindowView
import warlockfe.warlock3.compose.util.LocalLogger
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.prefs.defaultStyles
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.WindowLocation

@Composable
fun GameView(
    viewModel: GameViewModel,
    navigateToDashboard: () -> Unit,
) {
    val disconnected by viewModel.disconnected.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (disconnected) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(color = Color(red = 0xff, green = 0xcc, blue = 0))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("You have been disconnected from the server")
                FilledTonalButton(onClick = navigateToDashboard) {
                    Text("Back to dashboard")
                }
            }
        }

        val subWindows = viewModel.windowUiStates.collectAsState()
        val mainWindow = viewModel.mainWindowUiState.collectAsState()

        var openMenuId: Int? by remember { mutableStateOf(null) }
        var openMenu: WarlockMenuData? by remember { mutableStateOf(null) }

        LaunchedEffect(openMenuId) {
            if (openMenuId != null) {
                viewModel.menuData.collect { menuData ->
                    openMenu = menuData
                }
            } else {
                openMenu = null
            }
        }

        ActionContextMenu(
            menuData = openMenu,
            expectedMenuId = openMenuId,
            onDismiss = { openMenuId = null },
        )

        GameTextWindows(
            modifier = Modifier.fillMaxWidth().weight(1f),
            subWindowUiStates = subWindows.value,
            mainWindowUiState = mainWindow.value,
            selectedWindow = viewModel.selectedWindow.collectAsState().value,
            topHeight = viewModel.topHeight.collectAsState(null).value,
            bottomHeight = viewModel.bottomHeight.collectAsState(null).value,
            leftWidth = viewModel.leftWidth.collectAsState(null).value,
            rightWidth = viewModel.rightWidth.collectAsState(null).value,
            onActionClicked = { action ->
                when (action) {
                    is WarlockAction.SendCommand -> {
                        viewModel.sendCommand(action.command)
                    }

                    is WarlockAction.OpenMenu -> {
                        openMenuId = action.onClick()
                    }

                    else -> {
                        // Not our concern
                    }
                }
            },
            onMoveClicked = { name, location ->
                viewModel.moveWindow(name = name, location = location)
            },
            onWidthChanged = { name, width ->
                viewModel.setWindowWidth(name, width)
            },
            onHeightChanged = { name, height ->
                viewModel.setWindowHeight(name, height)
            },
            onSizeChanged = viewModel::setLocationSize,
            onSwapWindows = viewModel::changeWindowPositions,
            onCloseClicked = viewModel::closeWindow,
            saveStyle = viewModel::saveWindowStyle,
            onWindowSelected = viewModel::selectWindow,
            scrollEvents = viewModel.scrollEvents.collectAsState().value,
            handledScrollEvent = viewModel::handledScrollEvent,
            clearStream = viewModel::clearStream,
        )
        GameBottomBar(viewModel)
    }
}

@Composable
fun ActionContextMenu(
    menuData: WarlockMenuData?,
    expectedMenuId: Int?,
    onDismiss: () -> Unit,
) {
    val logger = LocalLogger.current
    val scope = rememberCoroutineScope()
    if (menuData != null) {
        if (expectedMenuId == menuData.id) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = onDismiss,
            ) {
                val groups = menuData.items.groupBy { it.category.split('-').first() }
                val categories = groups.keys.sorted()
                categories.forEach { category ->
                    val items = groups[category]!!
                    if (!category.contains('_')) {
                        items.forEach { item ->
                            logger.debug { "Menu item: $item" }
                            DropdownMenuItem(
                                text = {
                                    Text(item.label)
                                },
                                onClick = {
                                    scope.launch {
                                        item.action()
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    } else {
                        var expanded by remember(category) { mutableStateOf(false) }
                        DropdownMenuItem(
                            text = {
                                Text(category.split('_').getOrNull(1) ?: "Unknown")
                            },
                            onClick = { expanded = true },
                            trailingIcon = {
                                Icon(Arrow_right, contentDescription = "expandable")
                            }
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            val subgroups = items.groupBy { it.category.split('-').getOrNull(1) }
                            subgroups[null]?.forEach { item ->
                                DropdownMenuItem(
                                    text = {
                                        Text(item.label)
                                    },
                                    onClick = {
                                        scope.launch {
                                            item.action()
                                            onDismiss()
                                        }
                                    }
                                )
                            }
                            val subcatories = subgroups.keys.filterNotNull().sorted()
                            subcatories.forEach { category ->
                                var expanded by remember(category) { mutableStateOf(false) }
                                DropdownMenuItem(
                                    text = {
                                        Text(category)
                                    },
                                    onClick = { expanded = true },
                                    trailingIcon = {
                                        Icon(Arrow_right, contentDescription = "expandable")
                                    }
                                )
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    subgroups[category]?.forEach { item ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(item.label)
                                            },
                                            onClick = {
                                                scope.launch {
                                                    item.action()
                                                    onDismiss()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameTextWindows(
    modifier: Modifier,
    subWindowUiStates: List<WindowUiState>,
    mainWindowUiState: WindowUiState,
    selectedWindow: String,
    topHeight: Int?,
    bottomHeight: Int?,
    leftWidth: Int?,
    rightWidth: Int?,
    onActionClicked: (WarlockAction) -> Unit,
    onMoveClicked: (name: String, WindowLocation) -> Unit,
    onHeightChanged: (String, Int) -> Unit,
    onWidthChanged: (String, Int) -> Unit,
    onSizeChanged: (WindowLocation, Int) -> Unit,
    onSwapWindows: (WindowLocation, Int, Int) -> Unit,
    onCloseClicked: (String) -> Unit,
    saveStyle: (String, StyleDefinition) -> Unit,
    onWindowSelected: (String) -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    clearStream: (String) -> Unit,
) {
    // Container for all window views
    Row(modifier = modifier) {
        // Left column
        WindowsAtLocation(
            location = WindowLocation.LEFT,
            size = leftWidth,
            windowUiStates = subWindowUiStates,
            horizontalPanel = true,
            handleBefore = false,
            selectedWindow = selectedWindow,
            onSizeChanged = { onSizeChanged(WindowLocation.LEFT, it) },
            onActionClicked = onActionClicked,
            onMoveClicked = onMoveClicked,
            onHeightChanged = onHeightChanged,
            onWidthChanged = onWidthChanged,
            onSwapWindows = onSwapWindows,
            onCloseClicked = onCloseClicked,
            saveStyle = saveStyle,
            onWindowSelected = onWindowSelected,
            scrollEvents = scrollEvents,
            handledScrollEvent = handledScrollEvent,
            clearStream = clearStream,
        )
        // Center column
        Column(modifier = Modifier.weight(1f)) {
            WindowsAtLocation(
                location = WindowLocation.TOP,
                size = topHeight,
                windowUiStates = subWindowUiStates,
                horizontalPanel = false,
                handleBefore = false,
                selectedWindow = selectedWindow,
                onSizeChanged = { onSizeChanged(WindowLocation.TOP, it) },
                onActionClicked = onActionClicked,
                onMoveClicked = onMoveClicked,
                onHeightChanged = onHeightChanged,
                onWidthChanged = onWidthChanged,
                onSwapWindows = onSwapWindows,
                onCloseClicked = onCloseClicked,
                saveStyle = saveStyle,
                onWindowSelected = onWindowSelected,
                scrollEvents = scrollEvents,
                handledScrollEvent = handledScrollEvent,
                clearStream = clearStream,
            )
            WindowView(
                modifier = Modifier.fillMaxWidth().weight(1f), //.focusRequester(focusRequester),
                uiState = mainWindowUiState,
                isSelected = selectedWindow == mainWindowUiState.name,
                onActionClicked = onActionClicked,
                onMoveClicked = {},
                onMoveTowardsStart = null,
                onMoveTowardsEnd = null,
                onCloseClicked = {},
                saveStyle = {
                    saveStyle(mainWindowUiState.name, it)
                },
                onSelected = { onWindowSelected(mainWindowUiState.name) },
                scrollEvents = scrollEvents,
                handledScrollEvent = handledScrollEvent,
                clearStream = { clearStream(mainWindowUiState.name) },
            )
            WindowsAtLocation(
                location = WindowLocation.BOTTOM,
                size = bottomHeight,
                windowUiStates = subWindowUiStates,
                horizontalPanel = false,
                handleBefore = true,
                selectedWindow = selectedWindow,
                onSizeChanged = { onSizeChanged(WindowLocation.BOTTOM, it) },
                onActionClicked = onActionClicked,
                onMoveClicked = onMoveClicked,
                onHeightChanged = onHeightChanged,
                onWidthChanged = onWidthChanged,
                onSwapWindows = onSwapWindows,
                onCloseClicked = onCloseClicked,
                saveStyle = saveStyle,
                onWindowSelected = onWindowSelected,
                scrollEvents = scrollEvents,
                handledScrollEvent = handledScrollEvent,
                clearStream = clearStream,
            )
        }
        // Right Column
        WindowsAtLocation(
            location = WindowLocation.RIGHT,
            size = rightWidth,
            windowUiStates = subWindowUiStates,
            horizontalPanel = true,
            handleBefore = true,
            selectedWindow = selectedWindow,
            onSizeChanged = { onSizeChanged(WindowLocation.RIGHT, it) },
            onActionClicked = onActionClicked,
            onMoveClicked = onMoveClicked,
            onHeightChanged = onHeightChanged,
            onWidthChanged = onWidthChanged,
            onSwapWindows = onSwapWindows,
            onCloseClicked = onCloseClicked,
            saveStyle = saveStyle,
            onWindowSelected = onWindowSelected,
            scrollEvents = scrollEvents,
            handledScrollEvent = handledScrollEvent,
            clearStream = clearStream,
        )
    }
}

@Composable
fun WindowsAtLocation(
    location: WindowLocation,
    size: Int?,
    windowUiStates: List<WindowUiState>,
    horizontalPanel: Boolean,
    handleBefore: Boolean,
    selectedWindow: String,
    onSizeChanged: (Int) -> Unit,
    onActionClicked: (WarlockAction) -> Unit,
    onMoveClicked: (String, WindowLocation) -> Unit,
    onHeightChanged: (String, Int) -> Unit,
    onWidthChanged: (String, Int) -> Unit,
    onSwapWindows: (WindowLocation, Int, Int) -> Unit,
    onCloseClicked: (String) -> Unit,
    saveStyle: (String, StyleDefinition) -> Unit,
    onWindowSelected: (String) -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    clearStream: (String) -> Unit,
) {
    val windows = windowUiStates.filter { it.window?.location == location }.sortedBy { it.window?.position }
    if (windows.isNotEmpty()) {
        val panelState = remember(size == null) {
            ResizablePanelState(initialSize = size?.dp ?: 0.dp, minSize = 16.dp)
        }
        ResizablePanel(
            isHorizontal = horizontalPanel,
            handleBefore = handleBefore,
            state = panelState,
        ) {
            val content = @Composable {
                WindowViews(
                    windowStates = windows,
                    selectedWindow = selectedWindow,
                    onActionClicked = onActionClicked,
                    isHorizontal = !horizontalPanel,
                    onMoveClicked = onMoveClicked,
                    onHeightChanged = onHeightChanged,
                    onWidthChanged = onWidthChanged,
                    onSwapWindows = onSwapWindows,
                    onCloseClicked = onCloseClicked,
                    saveStyle = saveStyle,
                    onWindowSelected = onWindowSelected,
                    scrollEvents = scrollEvents,
                    handledScrollEvent = handledScrollEvent,
                    clearStream = clearStream,
                )
            }
            if (horizontalPanel) {
                Column {
                    content()
                }
            } else {
                Row {
                    content()
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
fun GameBottomBar(viewModel: GameViewModel) {
    val properties = viewModel.properties.collectAsState()

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val presets by viewModel.presets.collectAsState(emptyMap())
                val style = presets["default"] ?: defaultStyles["default"]
                val backgroundColor = style?.backgroundColor?.toColor() ?: Color.Unspecified
                val textColor = style?.textColor?.toColor()
                    ?: MaterialTheme.colorScheme.contentColorFor(backgroundColor)
                WarlockEntry(
                    modifier = Modifier.weight(1f),
                    backgroundColor = backgroundColor,
                    textColor = textColor,
                    viewModel = viewModel,
                )
                IndicatorView(
                    backgroundColor = backgroundColor,
                    defaultColor = textColor,
                    properties = properties.value,
                )
            }
            val vitalBars by viewModel.vitalBars.collectAsState(emptyList())
            DialogContent(
                dataObjects = vitalBars,
                modifier = Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 2.dp),
                executeCommand = {
                    // Cannot execute commands from vitals bar
                }
            )
            HandsView(viewModel.properties.collectAsState().value)
        }
        CompassView(
            modifier = Modifier.padding(4.dp),
            state = viewModel.compassState.collectAsState().value,
            theme = viewModel.compassTheme,
            onClick = {
                viewModel.sendCommand(it.abbreviation)
            }
        )
    }
}

@Composable
fun WindowViews(
    windowStates: List<WindowUiState>,
    selectedWindow: String,
    isHorizontal: Boolean,
    onActionClicked: (WarlockAction) -> Unit,
    onMoveClicked: (String, WindowLocation) -> Unit,
    onWidthChanged: (String, Int) -> Unit,
    onHeightChanged: (String, Int) -> Unit,
    onSwapWindows: (WindowLocation, Int, Int) -> Unit,
    onCloseClicked: (String) -> Unit,
    saveStyle: (String, StyleDefinition) -> Unit,
    onWindowSelected: (String) -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    clearStream: (String) -> Unit,
) {
    windowStates.forEachIndexed { index, uiState ->
        val content = @Composable { modifier: Modifier ->
            WindowView(
                modifier = modifier,
                uiState = uiState,
                isSelected = selectedWindow == uiState.name,
                onActionClicked = onActionClicked,
                onMoveClicked = { onMoveClicked(uiState.name, it) },
                onMoveTowardsStart = if (index > 0) {
                    { onSwapWindows(uiState.window!!.location!!, index, index - 1) }
                } else null,
                onMoveTowardsEnd = if (index < windowStates.lastIndex) {
                    { onSwapWindows(uiState.window!!.location!!, index, index + 1) }
                } else null,
                onCloseClicked = { onCloseClicked(uiState.name) },
                saveStyle = { saveStyle(uiState.name, it) },
                onSelected = { onWindowSelected(uiState.name) },
                scrollEvents = scrollEvents,
                handledScrollEvent = handledScrollEvent,
                clearStream = { clearStream(uiState.name) },
            )
        }

        if (index != windowStates.lastIndex) {
            val panelState = remember(uiState.name) {
                val size = if (isHorizontal) uiState.window?.width else uiState.window?.height
                ResizablePanelState(initialSize = size?.dp ?: 160.dp, minSize = 16.dp)
            }
            ResizablePanel(
                modifier = if (isHorizontal) Modifier.fillMaxHeight() else Modifier.fillMaxWidth(),
                isHorizontal = isHorizontal,
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