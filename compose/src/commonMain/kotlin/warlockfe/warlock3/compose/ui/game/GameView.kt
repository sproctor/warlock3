package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.components.CompassView
import warlockfe.warlock3.compose.components.ResizablePanel
import warlockfe.warlock3.compose.components.ResizablePanelState
import warlockfe.warlock3.compose.ui.components.HandsView
import warlockfe.warlock3.compose.ui.components.IndicatorView
import warlockfe.warlock3.compose.ui.components.VitalBars
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.compose.ui.window.WindowView
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.WindowLocation

@Composable
fun GameView(
    viewModel: GameViewModel,
    navigateToDashboard: () -> Unit,
) {
    val connected by viewModel.client.connected.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        if (!connected) {
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

        val subWindows = viewModel.windowUiStates.collectAsState(emptyList())
        val mainWindow = viewModel.mainWindowUiState.collectAsState()
        GameTextWindows(
            subWindowUiStates = subWindows.value,
            mainWindowUiState = mainWindow.value,
            topHeight = viewModel.topHeight.collectAsState(null).value,
            leftWidth = viewModel.leftWidth.collectAsState(null).value,
            rightWidth = viewModel.rightWidth.collectAsState(null).value,
            onActionClicked = {
                viewModel.sendCommand(it)
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
            onLeftChanged = viewModel::setLeftWidth,
            onRightChanged = viewModel::setRightWidth,
            onTopChanged = viewModel::setTopHeight,
            onSwapWindows = viewModel::changeWindowPositions,
            onCloseClicked = viewModel::closeWindow,
            saveStyle = viewModel::saveWindowStyle
        )
        GameBottomBar(viewModel)
    }
}

@Composable
fun ColumnScope.GameTextWindows(
    subWindowUiStates: List<WindowUiState>,
    mainWindowUiState: WindowUiState,
    topHeight: Int?,
    leftWidth: Int?,
    rightWidth: Int?,
    onActionClicked: (String) -> Unit,
    onMoveClicked: (name: String, WindowLocation) -> Unit,
    onHeightChanged: (String, Int) -> Unit,
    onWidthChanged: (String, Int) -> Unit,
    onTopChanged: (Int) -> Unit,
    onLeftChanged: (Int) -> Unit,
    onRightChanged: (Int) -> Unit,
    onSwapWindows: (WindowLocation, Int, Int) -> Unit,
    onCloseClicked: (String) -> Unit,
    saveStyle: (String, StyleDefinition) -> Unit,
) {
    // Container for all window views
    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
        // Left column
        val leftWindows =
            subWindowUiStates.filter { it.window?.location == WindowLocation.LEFT }.sortedBy { it.window?.position }
        if (leftWindows.isNotEmpty()) {
            val panelState = remember(leftWidth == null) {
                ResizablePanelState(initialSize = leftWidth?.dp ?: 0.dp, minSize = 16.dp)
            }
            ResizablePanel(
                isHorizontal = true,
                state = panelState,
            ) {
                Column {
                    WindowViews(
                        windowStates = leftWindows,
                        onActionClicked = onActionClicked,
                        isHorizontal = false,
                        onMoveClicked = onMoveClicked,
                        onHeightChanged = onHeightChanged,
                        onWidthChanged = onWidthChanged,
                        onSwapWindows = onSwapWindows,
                        onCloseClicked = onCloseClicked,
                        saveStyle = saveStyle,
                    )
                }
            }
            LaunchedEffect(panelState.currentSize) {
                if (leftWidth != null) {
                    onLeftChanged(panelState.currentSize.value.toInt())
                }
            }
        }
        // Center column
        Column(modifier = Modifier.weight(1f)) {
            val topWindows =
                subWindowUiStates.filter { it.window?.location == WindowLocation.TOP }.sortedBy { it.window?.position }
            if (topWindows.isNotEmpty()) {
                val panelState = remember(topHeight == null) {
                    ResizablePanelState(initialSize = topHeight?.dp ?: 0.dp, minSize = 16.dp)
                }
                ResizablePanel(
                    isHorizontal = false,
                    state = panelState,
                ) {
                    Row {
                        WindowViews(
                            windowStates = topWindows,
                            onActionClicked = onActionClicked,
                            isHorizontal = true,
                            onMoveClicked = onMoveClicked,
                            onHeightChanged = onHeightChanged,
                            onWidthChanged = onWidthChanged,
                            onSwapWindows = onSwapWindows,
                            onCloseClicked = onCloseClicked,
                            saveStyle = saveStyle,
                        )
                    }
                }
                LaunchedEffect(panelState.currentSize) {
                    if (topHeight != null) {
                        onTopChanged(panelState.currentSize.value.toInt())
                    }
                }
            }
            WindowView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                uiState = mainWindowUiState,
                onActionClicked = onActionClicked,
                onMoveClicked = {},
                onMoveTowardsStart = null,
                onMoveTowardsEnd = null,
                onCloseClicked = {},
                saveStyle = {
                    saveStyle(mainWindowUiState.name, it)
                }
            )
        }
        // Right Column
        val rightWindows =
            subWindowUiStates.filter { it.window?.location == WindowLocation.RIGHT }.sortedBy { it.window?.position }
        if (rightWindows.isNotEmpty()) {
            val panelState = remember(rightWidth == null) {
                ResizablePanelState(initialSize = rightWidth?.dp ?: 0.dp, minSize = 16.dp)
            }
            ResizablePanel(
                isHorizontal = true,
                handleBefore = true,
                state = panelState,
            ) {
                Column {
                    WindowViews(
                        windowStates = rightWindows,
                        onActionClicked = onActionClicked,
                        isHorizontal = false,
                        onMoveClicked = onMoveClicked,
                        onHeightChanged = onHeightChanged,
                        onWidthChanged = onWidthChanged,
                        onSwapWindows = onSwapWindows,
                        onCloseClicked = onCloseClicked,
                        saveStyle = saveStyle,
                    )
                }
            }
            LaunchedEffect(panelState.currentSize) {
                if (rightWidth != null) {
                    onRightChanged(panelState.currentSize.value.toInt())
                }
            }
        }
    }
}

@Composable
fun GameBottomBar(viewModel: GameViewModel) {
    val properties = viewModel.properties.collectAsState()

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth().padding(2.dp)) {
                WarlockEntry(modifier = Modifier.height(32.dp).weight(1f), viewModel = viewModel)
                IndicatorView(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .height(32.dp)
                        .background(Color(25, 25, 50)),
                    properties = properties.value,
                )
            }
            VitalBars(viewModel.vitalBars)
            HandsView(viewModel.properties.collectAsState().value)
        }
        CompassView(
            state = viewModel.compassState.value,
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
    isHorizontal: Boolean,
    onActionClicked: (String) -> Unit,
    onMoveClicked: (String, WindowLocation) -> Unit,
    onWidthChanged: (String, Int) -> Unit,
    onHeightChanged: (String, Int) -> Unit,
    onSwapWindows: (WindowLocation, Int, Int) -> Unit,
    onCloseClicked: (String) -> Unit,
    saveStyle: (String, StyleDefinition) -> Unit,
) {
    windowStates.forEachIndexed { index, uiState ->
        val panelState = remember(uiState.name) {
            val size = if (isHorizontal) uiState.window?.width else uiState.window?.height
            ResizablePanelState(initialSize = size?.dp ?: 160.dp, minSize = 16.dp)
        }
        ResizablePanel(
            modifier = if (isHorizontal) Modifier.fillMaxHeight() else Modifier.fillMaxWidth(),
            isHorizontal = isHorizontal,
            state = panelState,
        ) {
            WindowView(
                modifier = Modifier.matchParentSize(),
                uiState = uiState,
                onActionClicked = onActionClicked,
                onMoveClicked = { onMoveClicked(uiState.name, it) },
                onMoveTowardsStart = if (index > 0) {
                    { onSwapWindows(uiState.window!!.location!!, index, index - 1) }
                } else null,
                onMoveTowardsEnd = if (index < windowStates.lastIndex) {
                    { onSwapWindows(uiState.window!!.location!!, index, index + 1) }
                } else null,
                onCloseClicked = { onCloseClicked(uiState.name) },
                saveStyle = { saveStyle(uiState.name, it) }
            )
        }
        LaunchedEffect(panelState.currentSize) {
            val size = panelState.currentSize.value.toInt()
            if (isHorizontal) {
                onWidthChanged(uiState.name, size)
            } else {
                onHeightChanged(uiState.name, size)
            }
        }
    }
}