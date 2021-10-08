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
import cc.warlock.warlock3.app.viewmodel.CompassViewModel
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import cc.warlock.warlock3.app.viewmodel.VitalsViewModel
import cc.warlock.warlock3.core.Window
import cc.warlock.warlock3.core.WindowLocation

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameView(viewModel: GameViewModel) {
    val vitalsViewModel = remember(viewModel.client) { VitalsViewModel(viewModel.client) }
    val windows by viewModel.windows.collectAsState()
    val openWindows by viewModel.openWindows.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        // Container for all window views
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Left column
            Column(modifier = Modifier.width(200.dp)) {
                WindowViews(
                    location = WindowLocation.LEFT,
                    openWindows = openWindows,
                    windows = windows,
                    viewModel = viewModel,
                )
            }
            // Center column
            Column(modifier = Modifier.weight(1f)) {
                WindowViews(
                    location = WindowLocation.TOP,
                    openWindows = openWindows,
                    windows = windows,
                    viewModel = viewModel,
                )
                WindowViews(
                    location = WindowLocation.MAIN,
                    openWindows = openWindows,
                    windows = windows,
                    viewModel = viewModel,
                )
            }
            // Right Column
            Column {
                WindowViews(
                    location = WindowLocation.RIGHT,
                    openWindows = openWindows,
                    windows = windows,
                    viewModel = viewModel,
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
    windows: Map<String, Window>,
    openWindows: Set<String>,
    viewModel: GameViewModel,
) {
    windows.forEach { entry ->
        val window = entry.value
        if (openWindows.contains(entry.key) && window.location == location) {
            WindowView(name = entry.key, viewModel = viewModel)
        }
    }
}