package cc.warlock.warlock3.app.view

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.app.viewmodel.CompassViewModel
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import cc.warlock.warlock3.app.viewmodel.VitalsViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameView(viewModel: GameViewModel) {
    val vitalsViewModel = remember(viewModel.client) { VitalsViewModel(viewModel.client) }
    Column(modifier = Modifier.fillMaxSize()) {
        MainGameView(viewModel)
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

@Composable
fun ColumnScope.MainGameView(viewModel: GameViewModel) {
    val lines by viewModel.lines.collectAsState()
    val backgroundColor by viewModel.backgroundColor.collectAsState()
    val scrollState = rememberLazyListState()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(backgroundColor),
    ) {
        val height = this.maxHeight
        SelectionContainer {
            val textColor by viewModel.textColor.collectAsState()
            CompositionLocalProvider(LocalTextStyle provides TextStyle(color = textColor)) {
                Row(modifier = Modifier.matchParentSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .height(height),
                        state = scrollState
                    ) {
                        items(lines) { line ->
                            Text(line)
                        }
                    }
                    if (scrollState.isScrolledToEnd()) {
                        LaunchedEffect(lines) {
                            scrollState.scrollToItem(lines.lastIndex)
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                    )
                }
            }
        }
    }
}

fun LazyListState.isScrolledToEnd() = layoutInfo.visibleItemsInfo.lastOrNull()?.index == layoutInfo.totalItemsCount - 1