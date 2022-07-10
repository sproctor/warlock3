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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
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
    var showContextMenu by remember { mutableStateOf(false) }
    var showWindowSettingsDialog by remember { mutableStateOf(false) }

    Box(modifier.padding(2.dp)) {
        Surface(
            Modifier.onClick(
                matcher = PointerMatcher.mouse(PointerButton.Secondary),
                onClick = {
                    showContextMenu = true
                }
            ),
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
                    IconButton(
                        modifier = Modifier.size(16.dp),
                        onClick = { showContextMenu = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colors.onPrimary,
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
    CursorDropdownMenu(
        expanded = showContextMenu,
        onDismissRequest = {
            showContextMenu = false
        }
    ) {
        Column {
            Text(
                modifier = Modifier.clickable {
                    showWindowSettingsDialog = true
                    showContextMenu = false
                },
                text = "Window Settings ..."
            )
            uiState.window?.location?.let { location ->
                if (location != WindowLocation.MAIN) {
                    if (location != WindowLocation.LEFT) {
                        Text(
                            text = "Move to left column",
                            modifier = Modifier.clickable {
                                showContextMenu = false
                                onMoveClicked(WindowLocation.LEFT)
                            }
                        )
                    }
                    if (location != WindowLocation.TOP) {
                        Text(
                            text = "Move to center column",
                            modifier = Modifier.clickable {
                                showContextMenu = false
                                onMoveClicked(WindowLocation.TOP)
                            }
                        )
                    }
                    if (location != WindowLocation.RIGHT) {
                        Text(
                            text = "Move to right column",
                            modifier = Modifier.clickable {
                                showContextMenu = false
                                onMoveClicked(WindowLocation.RIGHT)
                            }
                        )
                    }
                }
                if (onMoveTowardsStart != null) {
                    Text(
                        text = "Move towards start",
                        modifier = Modifier.clickable {
                            showContextMenu = false
                            onMoveTowardsStart()
                        }
                    )
                }
                if (onMoveTowardsEnd != null) {
                    Text(
                        text = "Move towards end",
                        modifier = Modifier.clickable {
                            showContextMenu = false
                            onMoveTowardsEnd()
                        }
                    )
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
    var lastSerial by remember { mutableStateOf(0L) }
    val defaultStyle = styleMap["default"]
    val backgroundColor =
        (window.backgroundColor.specifiedOrNull() ?: defaultStyle?.backgroundColor)?.toColor() ?: Color.Unspecified
    val textColor = (window.textColor.specifiedOrNull() ?: defaultStyle?.textColor)?.toColor() ?: Color.Unspecified
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
                items(lines) { line ->
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
                                style = TextStyle(color = textColor, fontFamily = fontFamily, fontSize = fontSize),
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