package cc.warlock.warlock3.app.view

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.app.viewmodel.CompassViewModel
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import cc.warlock.warlock3.app.viewmodel.VitalsViewModel
import cc.warlock.warlock3.app.viewmodel.WindowViewModel
import cc.warlock.warlock3.core.Window
import cc.warlock.warlock3.core.WindowLocation
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameView(viewModel: GameViewModel) {
    val vitalsViewModel = remember(viewModel.client) { VitalsViewModel(viewModel.client) }
    val windowViewModels = remember { mutableMapOf<String, WindowViewModel>() }
    val windows by viewModel.windows.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        // Container for all window views
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Left column
            Column(modifier = Modifier.width(200.dp)) {
                WindowViews(
                    location = WindowLocation.LEFT,
                    client = viewModel.client,
                    windowViewModels = windowViewModels,
                    openWindowsState = viewModel.openWindows,
                    windows = windows
                )
            }
            // Center column
            Column(modifier = Modifier.weight(1f)) {
                WindowViews(
                    location = WindowLocation.TOP,
                    client = viewModel.client,
                    windowViewModels = windowViewModels,
                    openWindowsState = viewModel.openWindows,
                    windows = windows
                )
                WindowViews(
                    location = WindowLocation.MAIN,
                    client = viewModel.client,
                    windowViewModels = windowViewModels,
                    openWindowsState = viewModel.openWindows,
                    windows = windows
                )
            }
            // Right Column
            Column {
                WindowViews(
                    location = WindowLocation.RIGHT,
                    client = viewModel.client,
                    windowViewModels = windowViewModels,
                    openWindowsState = viewModel.openWindows,
                    windows = windows
                )
            }
        }
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
}

fun LazyListState.isScrolledToEnd() = layoutInfo.visibleItemsInfo.lastOrNull()?.index == layoutInfo.totalItemsCount - 1

@Composable
fun ColumnScope.WindowViews(
    location: WindowLocation,
    windowViewModels: MutableMap<String, WindowViewModel>,
    client: StormfrontClient,
    windows: Map<String, Window>,
    openWindowsState: StateFlow<List<String>>,
) {
    val openWindows by openWindowsState.collectAsState()
    windows.forEach { entry ->
        val window = entry.value
        if (openWindows.contains(entry.key) && window.location == location) {
            val windowViewModel = windowViewModels[entry.key]
                ?: WindowViewModel(
                    name = entry.key,
                    client = client,
                    showPrompts = entry.key == "main",
                    openWindows = openWindowsState,
                )
            windowViewModels[entry.key] = windowViewModel
            WindowView(windowViewModel)
        }
    }
}