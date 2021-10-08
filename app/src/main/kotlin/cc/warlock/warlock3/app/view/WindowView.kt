package cc.warlock.warlock3.app.view

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import cc.warlock.warlock3.app.viewmodel.toAnnotatedString
import cc.warlock.warlock3.app.viewmodel.toColor
import kotlinx.coroutines.launch

@Composable
fun ColumnScope.WindowView(name: String, viewModel: GameViewModel) {
    val lines by viewModel.client.getStream(name).lines.collectAsState()
    val backgroundColor by viewModel.defaultBackgroundColor.collectAsState()
    val scrollState = rememberLazyListState()
    val components = viewModel.components.collectAsState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(backgroundColor),
    ) {
        val height = this.maxHeight
        SelectionContainer {
            val textColor by viewModel.defaultTextColor.collectAsState()
            CompositionLocalProvider(LocalTextStyle provides TextStyle(color = textColor)) {
                Row(modifier = Modifier.matchParentSize()) {
                    val shouldScroll = scrollState.isScrolledToEnd()
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .height(height),
                        state = scrollState
                    ) {
                        items(lines) { line ->
                            Box(
                                modifier = Modifier.fillParentMaxWidth()
                                    .background(line.backgroundColor?.toColor() ?: Color.Unspecified)
                            ) {
                                Text(text = line.stringFactory(components.value).toAnnotatedString())
                            }
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                    )
                    if (shouldScroll) {
                        val scope = rememberCoroutineScope()
                        SideEffect {
                            scope.launch {
                                scrollState.scrollToItem(lines.lastIndex)
                            }
                        }
                    }
                }
            }
        }
    }
}
