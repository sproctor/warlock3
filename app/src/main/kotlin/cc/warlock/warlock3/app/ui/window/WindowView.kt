package cc.warlock.warlock3.app.ui.window

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.app.model.ViewHighlight
import cc.warlock.warlock3.app.util.getEntireLineStyles
import cc.warlock.warlock3.app.util.highlight
import cc.warlock.warlock3.app.util.toAnnotatedString
import cc.warlock.warlock3.app.util.toColor
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.flattenStyles
import cc.warlock.warlock3.stormfront.StreamLine
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.lang.Integer.max
import java.net.URI

@Composable
fun WindowView(
    modifier: Modifier,
    uiState: WindowUiState,
    onActionClicked: (String) -> Unit,
) {
    Box(modifier.padding(2.dp)) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, Color.Black),
            elevation = 4.dp
        ) {
            Column {
                Box(
                    Modifier.background(MaterialTheme.colors.primary).fillMaxWidth()
                        .padding(4.dp)
                ) {
                    Text(
                        text = (uiState.window?.title ?: "") + (uiState.window?.subtitle ?: ""),
                        color = MaterialTheme.colors.onPrimary,
                    )
                }
                val lines by uiState.lines.collectAsState()
                WindowViewContent(
                    lines = lines,
                    components = uiState.components,
                    highlights = uiState.highlights,
                    styleMap = uiState.presets,
                    onActionClicked = onActionClicked
                )
            }
        }
    }
}

@Composable
private fun WindowViewContent(
    lines: List<StreamLine>,
    components: Map<String, StyledString>,
    highlights: List<ViewHighlight>,
    styleMap: Map<String, StyleDefinition>,
    onActionClicked: (String) -> Unit
) {
    val scrollState = rememberLazyListState()
    var lastSerial by remember { mutableStateOf(0L) }
    val backgroundColor = styleMap["default"]?.backgroundColor?.toColor() ?: Color.Unspecified
    val textColor = styleMap["default"]?.textColor?.toColor() ?: Color.Unspecified

    Box(Modifier.background(backgroundColor).padding(vertical = 4.dp)) {
        // TODO: reimplement this in a way that passes clicks through to clickable text
//        SelectionContainer {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = LocalScrollbarStyle.current.thickness),
            state = scrollState
        ) {
            items(lines) { line ->
                val annotatedString = line.text.toAnnotatedString(variables = components, styleMap = styleMap)
                val lineStyle = flattenStyles(
                    line.text.getEntireLineStyles(
                        variables = components,
                        styleMap = styleMap,
                    )
                )
                if (!line.ignoreWhenBlank || annotatedString.isNotBlank()) {
                    val highlightedLine = annotatedString.highlight(highlights)
                    Box(
                        modifier = Modifier.fillParentMaxWidth()
                            .background(lineStyle?.backgroundColor?.toColor() ?: Color.Unspecified)
                            .padding(horizontal = 4.dp)
                    ) {
                        ClickableText(text = highlightedLine, style = TextStyle(color = textColor)) { offset ->
                            println("handling click: $offset")
                            highlightedLine.getStringAnnotations(start = offset, end = offset)
                                .forEach { annotation ->
                                    when (annotation.tag) {
                                        "action" -> {
                                            println("action clicked: ${annotation.item}")
                                            onActionClicked(annotation.item)
                                        }
                                        "url" -> {
                                            try {
                                                Desktop.getDesktop().browse(URI(annotation.item))
                                            } catch (e: Exception) {
                                                // TODO: add some error handling here
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
            adapter = rememberScrollbarAdapter(scrollState),
        )
//        }
    }

    LaunchedEffect(lines) {
        // Wait for the current scroll to complete
        while (scrollState.isScrollInProgress) {
            delay(5)
        }
        // If we're at the spot we last scrolled to
        val lastVisibleSerial =
            scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { lines[it].serialNumber } ?: -1L
        if (lastVisibleSerial >= lastSerial) {
            // scroll to the end, and remember it
            lastSerial = lines.lastOrNull()?.serialNumber ?: -1L
            scrollState.scrollToItem(max(0, lines.lastIndex))
        }
    }
}