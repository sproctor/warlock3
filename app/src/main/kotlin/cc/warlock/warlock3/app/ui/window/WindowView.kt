package cc.warlock.warlock3.app.ui.window

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cc.warlock.warlock3.app.components.ColorPickerDialog
import cc.warlock.warlock3.app.model.ViewHighlight
import cc.warlock.warlock3.app.ui.settings.FontPickerDialog
import cc.warlock.warlock3.app.ui.settings.FontUpdate
import cc.warlock.warlock3.app.ui.settings.fontFamilyMap
import cc.warlock.warlock3.app.util.*
import cc.warlock.warlock3.core.text.*
import cc.warlock.warlock3.core.window.Window
import cc.warlock.warlock3.core.window.WindowLocation
import cc.warlock.warlock3.stormfront.stream.StreamLine
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.lang.Integer.max
import java.net.URI

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun WindowView(
    modifier: Modifier,
    uiState: WindowUiState,
    onActionClicked: (String) -> Unit,
    onMoveClicked: (WindowLocation) -> Unit,
    onMoveTowardsStart: (() -> Unit)?,
    onMoveTowardsEnd: (() -> Unit)?,
    onCloseClicked: () -> Unit,
    saveStyle: (StyleDefinition) -> Unit,
) {
    val window = uiState.window
    var showWindowSettingsDialog by remember { mutableStateOf(false) }

    val contextMenuItems = {
        buildList {
            add(
                ContextMenuItem(
                    onClick = {
                        showWindowSettingsDialog = true
                    },
                    label = "Window Settings ..."
                )
            )
            uiState.window?.location?.let { location ->
                if (location != WindowLocation.MAIN) {
                    if (location != WindowLocation.LEFT) {
                        add(
                            ContextMenuItem(
                                label = "Move to left column",
                                onClick = {
                                    onMoveClicked(WindowLocation.LEFT)
                                }
                            )
                        )
                    }
                    if (location != WindowLocation.TOP) {
                        add(
                            ContextMenuItem(
                                label = "Move to center column",
                                onClick = {
                                    onMoveClicked(WindowLocation.TOP)
                                }
                            )
                        )
                    }
                    if (location != WindowLocation.RIGHT) {
                        add(
                            ContextMenuItem(
                                label = "Move to right column",
                                onClick = {
                                    onMoveClicked(WindowLocation.RIGHT)
                                }
                            )
                        )
                    }
                }
                if (onMoveTowardsStart != null) {
                    add(
                        ContextMenuItem(
                            label = "Move towards start",
                            onClick = {
                                onMoveTowardsStart()
                            }
                        )
                    )
                }
                if (onMoveTowardsEnd != null) {
                    add(
                        ContextMenuItem(
                            label = "Move towards end",
                            onClick = {
                                onMoveTowardsEnd()
                            }
                        )
                    )
                }
            }
        }
    }
    ContextMenuDataProvider(
        items = contextMenuItems
    ) {
        Box(modifier.padding(2.dp)) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color.Black),
                elevation = 4.dp
            ) {
                Column {
                    Row(
                        Modifier.background(MaterialTheme.colors.primary).fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            Text(
                                text = (uiState.window?.title ?: "") + (uiState.window?.subtitle ?: ""),
                                color = MaterialTheme.colors.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (uiState.window?.location != WindowLocation.MAIN) {
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                modifier = Modifier.size(16.dp),
                                onClick = onCloseClicked
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colors.onPrimary,
                                )
                            }
                        }
                    }
                    if (window != null) {
                        val lines by uiState.lines.collectAsState()
                        WindowViewContent(
                            lines = lines,
                            window = window,
                            components = uiState.components,
                            highlights = uiState.highlights,
                            styleMap = uiState.presets,
                            onActionClicked = onActionClicked
                        )
                    }
                }
            }
        }
    }

    if (showWindowSettingsDialog && window != null) {
        WindowSettingsDialog(
            onCloseRequest = { showWindowSettingsDialog = false },
            style = StyleDefinition(
                textColor = window.textColor,
                backgroundColor = window.backgroundColor,
                fontFamily = window.fontFamily,
                fontSize = window.fontSize
            ),
            saveStyle = saveStyle,
        )
    }
}

@Composable
private fun WindowViewContent(
    lines: List<StreamLine>,
    window: Window,
    components: Map<String, StyledString>,
    highlights: List<ViewHighlight>,
    styleMap: Map<String, StyleDefinition>,
    onActionClicked: (String) -> Unit
) {
    val scrollState = rememberLazyListState()
    val defaultStyle = styleMap["default"]
    val backgroundColor =
        (window.backgroundColor.specifiedOrNull() ?: defaultStyle?.backgroundColor)?.toColor()
            ?: Color.Unspecified
    val textColor =
        (window.textColor.specifiedOrNull() ?: defaultStyle?.textColor)?.toColor() ?: Color.Unspecified
    val fontFamily = (window.fontFamily ?: defaultStyle?.fontFamily)?.let { fontFamilyMap[it] }
    val fontSize = (window.fontSize ?: defaultStyle?.fontSize)?.sp ?: defaultFontSize

    Box(Modifier.background(backgroundColor).padding(vertical = 4.dp)) {
        // TODO: reimplement this in a way that passes clicks through to clickable text
        SelectionContainer {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = LocalScrollbarStyle.current.thickness),
                state = scrollState
            ) {
                items(
                    items = lines,
                    key = { it.serialNumber }
                ) { line ->
                    val lineStyle = flattenStyles(
                        line.text.getEntireLineStyles(
                            variables = components,
                            styleMap = styleMap,
                        )
                    )
                    val annotatedString = buildAnnotatedString {
                        lineStyle?.let { pushStyle(it.toSpanStyle()) }
                        append(line.text.toAnnotatedString(variables = components, styleMap = styleMap))
                        if (lineStyle != null) pop()
                    }
                    if (!line.ignoreWhenBlank || annotatedString.isNotBlank()) {
                        val highlightedLine = annotatedString.highlight(highlights)
                        Box(
                            modifier = Modifier.fillParentMaxWidth()
                                .background(lineStyle?.backgroundColor?.toColor() ?: Color.Unspecified)
                                .padding(horizontal = 4.dp)
                        ) {
                            ClickableText(
                                text = highlightedLine,
                                style = TextStyle(
                                    color = textColor,
                                    fontFamily = fontFamily,
                                    fontSize = fontSize
                                ),
                            ) { offset ->
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
        }
        VerticalScrollbar(
            modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
            adapter = rememberScrollbarAdapter(scrollState),
        )
    }

    var lastSerial by remember { mutableStateOf(-1L) }
    var firstVisibleSerial by remember { mutableStateOf(-1L) }
    var firstVisibleIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(lines) {
        // Wait for the current scroll to complete
        while (scrollState.isScrollInProgress) {
            delay(5)
        }
        // If we're at the spot we last scrolled to
        val lastVisibleSerial =
            scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { lines[it].serialNumber } ?: -1L
        val oldLastSerial = lastSerial
        // remember the last serial
        lastSerial = lines.lastOrNull()?.serialNumber ?: -1L

        val oldFirstVisibleIndex = firstVisibleIndex
        firstVisibleIndex = scrollState.firstVisibleItemIndex
        val oldFirstVisibleSerial = firstVisibleSerial
        val currentFirstVisibleSerial = lines.getOrNull(scrollState.firstVisibleItemIndex)?.serialNumber ?: -1L

        if (lastVisibleSerial >= oldLastSerial) { // scroll to the end if we were at the end
            if (lines.lastIndex > 0)
                scrollState.scrollToItem(lines.lastIndex)
        } else { // Maintain scroll position when items are removed from the front of the list
            if (oldFirstVisibleIndex == firstVisibleIndex
                && oldFirstVisibleSerial != currentFirstVisibleSerial) {
                firstVisibleIndex = max(0, oldFirstVisibleIndex + (oldFirstVisibleSerial - currentFirstVisibleSerial).toInt())
                scrollState.scrollToItem(firstVisibleIndex)
            } else {
                firstVisibleSerial = currentFirstVisibleSerial
            }
        }
    }
}

@Composable
fun WindowSettingsDialog(
    onCloseRequest: () -> Unit,
    style: StyleDefinition,
    saveStyle: (StyleDefinition) -> Unit,
) {
    var editColor by remember { mutableStateOf<Pair<WarlockColor, (WarlockColor) -> Unit>?>(null) }
    var editFont by remember { mutableStateOf<Pair<StyleDefinition, (FontUpdate) -> Unit>?>(null) }

    if (editColor != null) {
        ColorPickerDialog(
            initialColor = editColor!!.first.toColor(),
            onCloseRequest = { editColor = null },
            onColorSelected = { color ->
                editColor?.second?.invoke(color ?: WarlockColor.Unspecified)
                editColor = null
            }
        )
    }
    if (editFont != null) {
        FontPickerDialog(
            currentStyle = editFont!!.first,
            onCloseRequest = { editFont = null },
            onSaveClicked = { fontUpdate ->
                editFont?.second?.invoke(fontUpdate)
                editFont = null
            }
        )
    }

    Dialog(
        title = "Window Settings",
        onCloseRequest = onCloseRequest
    ) {
        Column {
            OutlinedButton(
                onClick = {
                    editColor = Pair(style.textColor) { color ->
                        saveStyle(
                            style.copy(textColor = color)
                        )
                    }
                }
            ) {
                Row {
                    Text("Content: ")
                    Box(
                        Modifier.size(16.dp).background(style.textColor.toColor()).border(1.dp, Color.Black)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            OutlinedButton(
                onClick = {
                    editColor = Pair(style.backgroundColor) { color ->
                        saveStyle(
                            style.copy(backgroundColor = color)
                        )
                    }
                }
            ) {
                Row {
                    Text("Background: ")
                    Box(
                        Modifier.size(16.dp).background(style.backgroundColor.toColor())
                            .border(1.dp, Color.Black)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            OutlinedButton(
                onClick = {
                    editFont = Pair(style) { fontUpdate ->
                        saveStyle(style.copy(fontFamily = fontUpdate.fontFamily, fontSize = fontUpdate.size))
                    }
                }
            ) {
                Text("Font: ${style.fontFamily ?: "Default"} ${style.fontSize ?: "Default"}")
            }
            Spacer(Modifier.width(16.dp))
            Button(onClick = {
                saveStyle(StyleDefinition())
            }) {
                Text("Revert to defaults")
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}