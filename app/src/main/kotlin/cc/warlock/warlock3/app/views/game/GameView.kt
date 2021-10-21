package cc.warlock.warlock3.app.views.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.app.components.ResizablePanel
import cc.warlock.warlock3.app.components.ResizablePanelState
import cc.warlock.warlock3.app.viewmodel.CompassViewModel
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import cc.warlock.warlock3.app.viewmodel.VitalsViewModel
import cc.warlock.warlock3.app.viewmodel.WindowViewModel
import cc.warlock.warlock3.core.window.Window
import cc.warlock.warlock3.core.window.WindowLocation

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameView(
    viewModel: GameViewModel,
    windowViewModels: Map<String, WindowViewModel>,
    mainWindowViewModel: WindowViewModel,
) {
    val windows by viewModel.windows.collectAsState()
    val openWindows by viewModel.openWindows.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        GameTextWindows(
            windows = windows,
            openWindows = openWindows,
            windowViewModels = windowViewModels,
            mainWindowViewModel = mainWindowViewModel,
        )
        GameBottomBar(viewModel)
    }
}

@Composable
fun ColumnScope.GameTextWindows(
    windows: Map<String, Window>,
    openWindows: Set<String>,
    windowViewModels: Map<String, WindowViewModel>,
    mainWindowViewModel: WindowViewModel,
) {
    // Container for all window views
    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
        // Left column
        val leftWindows =
            windows.filter { openWindows.contains(it.key) && it.value.location == WindowLocation.LEFT }
        if (leftWindows.isNotEmpty()) {
            val panelState = remember { ResizablePanelState(initialSize = 200.dp, minSize = 8.dp) }
            ResizablePanel(
                modifier = Modifier,
                isHorizontal = true,
                state = panelState,
            ) {
                Column {
                    WindowViews(
                        windows = leftWindows,
                        windowViewModels = windowViewModels,
                    )
                }
            }
        }
        // Center column
        val topWindows =
            windows.filter { openWindows.contains(it.key) && it.value.location == WindowLocation.TOP }
        Column(modifier = Modifier.weight(1f)) {
            WindowViews(
                windows = topWindows,
                windowViewModels = windowViewModels,
            )
            WindowView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                viewModel = mainWindowViewModel,
            )
        }
        // Right Column
        val rightWindows =
            windows.filter { openWindows.contains(it.key) && it.value.location == WindowLocation.RIGHT }
        if (rightWindows.isNotEmpty()) {
            val panelState = remember { ResizablePanelState(initialSize = 200.dp, minSize = 8.dp) }
            ResizablePanel(
                modifier = Modifier,
                isHorizontal = true,
                handleBefore = true,
                state = panelState,
            ) {
                Column {
                    WindowViews(
                        windows = rightWindows,
                        windowViewModels = windowViewModels,
                    )
                }
            }
        }
    }
}

@Composable
fun GameBottomBar(viewModel: GameViewModel) {
    val vitalsViewModel = remember(viewModel.client) { VitalsViewModel(viewModel.client) }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth().padding(2.dp)) {
                WarlockEntry(modifier = Modifier.height(32.dp).weight(1f), viewModel = viewModel)
                IndicatorView(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .height(32.dp)
                        .background(Color(25, 25, 50)),
                    viewModel = viewModel,
                )
            }
            VitalBars(vitalsViewModel.vitalBars)
            HandsView(viewModel)
        }
        val compassViewModel = remember(viewModel.client) { CompassViewModel(viewModel.client) }
        CompassView(
            state = compassViewModel.compassState.value,
            theme = compassViewModel.theme,
        )
    }
}

@Composable
fun WindowViews(
    windows: Map<String, Window>,
    windowViewModels: Map<String, WindowViewModel>,
) {
    windows.forEach { entry ->
        val windowViewModel = windowViewModels[entry.key]
        if (windowViewModel != null) {
            val panelState = remember(entry.key) { ResizablePanelState(initialSize = 160.dp, minSize = 16.dp) }
            ResizablePanel(
                modifier = Modifier.fillMaxWidth(),
                isHorizontal = false,
                state = panelState,
            ) {
                WindowView(modifier = Modifier.matchParentSize(), viewModel = windowViewModel)
            }
        }
    }
}