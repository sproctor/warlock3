package cc.warlock.warlock3.app.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import cc.warlock.warlock3.app.viewmodel.CompassViewModel
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import cc.warlock.warlock3.app.viewmodel.VitalsViewModel
import cc.warlock.warlock3.app.viewmodel.WindowViewModel
import cc.warlock.warlock3.core.Window
import cc.warlock.warlock3.core.WindowLocation

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FrameWindowScope.GameView(viewModel: GameViewModel) {
    GameMenu(viewModel)
    Column(modifier = Modifier.fillMaxSize()) {
        GameTextWindows(viewModel)
        GameBottomBar(viewModel)
    }
}

fun LazyListState.isScrolledToEnd() = layoutInfo.visibleItemsInfo.lastOrNull()?.index == layoutInfo.totalItemsCount - 1

@Composable
fun ColumnScope.GameTextWindows(viewModel: GameViewModel) {
    val windows by viewModel.windows.collectAsState()
    val openWindows by viewModel.openWindows.collectAsState()
    val windowViewModels = remember { mutableStateOf(mapOf<String, WindowViewModel>()) }

    // Container for all window views
    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
        // Left column
        val leftWindows =
            windows.filter { openWindows.contains(it.key) && it.value.location == WindowLocation.LEFT }
        if (leftWindows.isNotEmpty()) {
            Column(modifier = Modifier.width(200.dp)) {
                WindowViews(
                    windows = leftWindows,
                    gameViewModel = viewModel,
                    windowViewModels = windowViewModels,
                )
            }
        }
        // Center column
        val topWindows =
            windows.filter { openWindows.contains(it.key) && it.value.location == WindowLocation.TOP }
        Column(modifier = Modifier.weight(1f)) {
            WindowViews(
                windows = topWindows,
                gameViewModel = viewModel,
                windowViewModels = windowViewModels,
            )
            val mainViewModel = windowViewModels.value["main"] ?: WindowViewModel("main", viewModel.client).also {
                windowViewModels.value += "main" to it
            }
            WindowView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                viewModel = mainViewModel,
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
                        gameViewModel = viewModel,
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
fun FrameWindowScope.GameMenu(viewModel: GameViewModel) {
    val windows by viewModel.windows.collectAsState()
    val openWindows by viewModel.openWindows.collectAsState()

    MenuBar {
        Menu("Settings") {
            Item("Placeholder", onClick = {})
        }

        Menu("Windows") {
            windows.values.forEach { window ->
                if (window.name != "main") {
                    CheckboxItem(
                        text = window.title,
                        checked = openWindows.any { it == window.name },
                        onCheckedChange = {
                            if (it) {
                                viewModel.showWindow(window.name)
                            } else {
                                viewModel.hideWindow(window.name)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WindowViews(
    windows: Map<String, Window>,
    gameViewModel: GameViewModel,
    windowViewModels: MutableState<Map<String, WindowViewModel>>,
) {
    windows.forEach { entry ->
        val panelState = remember(entry.key) { ResizablePanelState(initialSize = 160.dp, minSize = 16.dp) }
        ResizablePanel(
            modifier = Modifier.fillMaxWidth(),
            isHorizontal = false,
            state = panelState,
        ) {
            val windowViewModel = windowViewModels.value[entry.key] ?: WindowViewModel(entry.key, gameViewModel.client).also {
                windowViewModels.value += entry.key to it
            }
            WindowView(modifier = Modifier.matchParentSize(), viewModel = windowViewModel)
        }
    }
}