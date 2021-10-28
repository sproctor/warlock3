package cc.warlock.warlock3.app.views.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.app.util.highlight
import cc.warlock.warlock3.app.util.toAnnotatedString
import cc.warlock.warlock3.app.util.toColor
import cc.warlock.warlock3.app.viewmodel.WindowViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Integer.max

@Composable
fun WindowView(modifier: Modifier, viewModel: WindowViewModel) {
    Box(modifier.padding(2.dp)) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, Color.Black),
            elevation = 4.dp
        ) {
            Column {
                val window by viewModel.window.collectAsState(null)
                Box(
                    Modifier.background(MaterialTheme.colors.primary).fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = (window?.title ?: "") + (window?.subtitle ?: ""),
                        color = MaterialTheme.colors.onPrimary,
                    )
                }
                WindowViewContent(viewModel)
            }
        }
    }
}

@Composable
private fun WindowViewContent(viewModel: WindowViewModel) {
    val lines by viewModel.lines.collectAsState()
    val scrollState = rememberLazyListState()
    val components = viewModel.components.collectAsState()
    val scope = rememberCoroutineScope()
    var lastIndex by remember { mutableStateOf(0) }
    val highlights by viewModel.highlights.collectAsState(emptyList())

    BoxWithConstraints(Modifier.background(viewModel.backgroundColor.value).padding(vertical = 4.dp)) {
        val height = this.maxHeight
        SelectionContainer {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(color = viewModel.textColor.value)) {
                Row(modifier = Modifier.matchParentSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .height(height),
                        state = scrollState
                    ) {
                        items(lines) { line ->
                            val annotatedString = line.text.toAnnotatedString(components.value)
                            if (!line.ignoreWhenBlank || annotatedString.isNotBlank()) {
                                val highlightedLine = annotatedString.highlight(highlights)
                                Box(
                                    modifier = Modifier.fillParentMaxWidth()
                                        .background(line.style?.backgroundColor?.toColor() ?: Color.Unspecified)
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text(text = highlightedLine)
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                    )
                }
            }
        }
    }

    LaunchedEffect(lines) {
        // Wait for the current scroll to complete
        while (scrollState.isScrollInProgress) {
            delay(15)
        }
        // If we're at the spot we last scrolled to
        val lastVisibleIndex = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (lastVisibleIndex >= lastIndex) {
            scope.launch {
                // scroll to the end, and remember it
                scrollState.scrollToItem(max(0, lines.lastIndex))
                lastIndex = lines.lastIndex
            }
        }
    }
}