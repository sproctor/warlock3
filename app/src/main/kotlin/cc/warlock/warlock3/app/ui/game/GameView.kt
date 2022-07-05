package cc.warlock.warlock3.app.ui.game

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.app.components.CompassView
import cc.warlock.warlock3.app.components.ResizablePanel
import cc.warlock.warlock3.app.components.ResizablePanelState
import cc.warlock.warlock3.app.ui.components.HandsView
import cc.warlock.warlock3.app.ui.components.IndicatorView
import cc.warlock.warlock3.app.ui.components.VitalBars
import cc.warlock.warlock3.app.ui.window.WindowUiState
import cc.warlock.warlock3.app.ui.window.WindowView
import cc.warlock.warlock3.core.window.WindowLocation
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun GameView(
    viewModel: GameViewModel,
    navigateToDashboard: () -> Unit,
) {
    val connected by viewModel.client.connected.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        if (!connected) {
            Box(
                modifier = Modifier
                    .background(color = Color(red = 0xff, green = 0xcc, blue = 0))
                    .fillMaxWidth()
            ) {
                Column(Modifier.align(Alignment.Center).padding(16.dp)) {
                    Text("You have been disconnected from the server")
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(onClick = navigateToDashboard) {
                        Text("Back to dashboard")
                    }
                }
            }
        }
        val scrollbarStyle = LocalScrollbarStyle.current
        CompositionLocalProvider(
            LocalScrollbarStyle provides scrollbarStyle.copy(
                hoverColor = MaterialTheme.colors.primary,
                unhoverColor = MaterialTheme.colors.primary.copy(alpha = 0.42f)
            )
        ) {
            val subWindows = viewModel.windowUiStates.collectAsState(emptyList())
            val mainWindow = viewModel.mainWindowUiState.collectAsState(
                WindowUiState(
                    name = "main",
                    lines = MutableStateFlow(emptyList()),
                    window = null,
                    components = emptyMap(),
                    highlights = emptyList(),
                    presets = emptyMap()
                )
            )
            GameTextWindows(
                subWindowUiStates = subWindows.value,
                mainWindowUiState = mainWindow.value,
            )
            GameBottomBar(viewModel)
        }
    }
}

@Composable
fun ColumnScope.GameTextWindows(
    subWindowUiStates: List<WindowUiState>,
    mainWindowUiState: WindowUiState,
) {
    // Container for all window views
    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
        // Left column
        val leftWindows = subWindowUiStates.filter { it.window?.location == WindowLocation.LEFT }
        if (leftWindows.isNotEmpty()) {
            val panelState = remember { ResizablePanelState(initialSize = 200.dp, minSize = 8.dp) }
            ResizablePanel(
                modifier = Modifier,
                isHorizontal = true,
                state = panelState,
            ) {
                Column {
                    WindowViews(windowStates = leftWindows)
                }
            }
        }
        // Center column
        val topWindows = subWindowUiStates.filter { it.window?.location == WindowLocation.TOP }
        Column(modifier = Modifier.weight(1f)) {
            WindowViews(windowStates = topWindows)
            WindowView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                uiState = mainWindowUiState
            )
        }
        // Right Column
        val rightWindows = subWindowUiStates.filter { it.window?.location == WindowLocation.RIGHT }
        if (rightWindows.isNotEmpty()) {
            val panelState = remember { ResizablePanelState(initialSize = 200.dp, minSize = 8.dp) }
            ResizablePanel(
                modifier = Modifier,
                isHorizontal = true,
                handleBefore = true,
                state = panelState,
            ) {
                Column {
                    WindowViews(windowStates = rightWindows)
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
        )
    }
}

@Composable
fun WindowViews(
    windowStates: List<WindowUiState>,
) {
    windowStates.forEach { uiState ->
        val panelState = remember(uiState.name) { ResizablePanelState(initialSize = 160.dp, minSize = 16.dp) }
        ResizablePanel(
            modifier = Modifier.fillMaxWidth(),
            isHorizontal = false,
            state = panelState,
        ) {
            WindowView(modifier = Modifier.matchParentSize(), uiState = uiState)
        }
    }
}