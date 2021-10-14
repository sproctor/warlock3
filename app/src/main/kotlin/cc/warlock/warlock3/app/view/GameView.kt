package cc.warlock.warlock3.app.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import cc.warlock.warlock3.app.viewmodel.CompassViewModel
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import cc.warlock.warlock3.app.viewmodel.VitalsViewModel
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

    // Container for all window views
    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
        // Left column
        val leftWindows =
            windows.filter { openWindows.contains(it.key) && it.value.location == WindowLocation.LEFT }
        if (leftWindows.isNotEmpty()) {
            Column(modifier = Modifier.width(200.dp)) {
                WindowViews(
                    windows = leftWindows,
                    viewModel = viewModel,
                )
            }
        }
        // Center column
        val topWindows =
            windows.filter { openWindows.contains(it.key) && it.value.location == WindowLocation.TOP }
        Column(modifier = Modifier.weight(1f)) {
            WindowViews(
                windows = topWindows,
                viewModel = viewModel,
            )
            WindowView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                name = "main",
                viewModel = viewModel,
            )
        }
        // Right Column
        val rightWindows =
            windows.filter { openWindows.contains(it.key) && it.value.location == WindowLocation.RIGHT }
        if (rightWindows.isNotEmpty()) {
            Column {
                WindowViews(
                    windows = rightWindows,
                    viewModel = viewModel,
                )
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
            val vitalBars by vitalsViewModel.vitalBars.collectAsState()
            VitalBars(vitalBars)
            HandsView(viewModel)
        }
        val compassViewModel = remember(viewModel.client) { CompassViewModel(viewModel.client) }
        val compassState by compassViewModel.compassState.collectAsState()
        CompassView(
            state = compassState,
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
    viewModel: GameViewModel,
) {
    windows.forEach { entry ->
        val panelState = remember(entry.key) { ResizablePanelState(200.dp) }
        ResizablePanel(
            modifier = Modifier.fillMaxWidth(),
            isHorizontal = false,
            state = panelState,
        ) {
            WindowView(modifier = Modifier.matchParentSize(), name = entry.key, viewModel = viewModel)
        }
    }
}