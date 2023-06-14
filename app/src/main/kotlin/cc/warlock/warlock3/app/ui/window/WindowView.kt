package cc.warlock.warlock3.app.ui.window

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cc.warlock.warlock3.app.components.ColorPickerDialog
import cc.warlock.warlock3.app.model.ViewHighlight
import cc.warlock.warlock3.app.ui.game.toWindowLine
import cc.warlock.warlock3.app.ui.settings.FontPickerDialog
import cc.warlock.warlock3.app.ui.settings.FontUpdate
import cc.warlock.warlock3.app.ui.settings.fontFamilyMap
import cc.warlock.warlock3.app.util.defaultFontSize
import cc.warlock.warlock3.app.util.toColor
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.text.WarlockColor
import cc.warlock.warlock3.core.text.specifiedOrNull
import cc.warlock.warlock3.core.window.Window
import cc.warlock.warlock3.core.window.WindowLocation
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.net.URI

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
        Surface(
            modifier.padding(2.dp),
            shape = MaterialTheme.shapes.extraSmall,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column {
                Row(
                    Modifier.background(MaterialTheme.colorScheme.primary).fillMaxWidth()
                        .padding(4.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        Text(
                            text = (uiState.window?.title ?: "") + (uiState.window?.subtitle ?: ""),
                            color = MaterialTheme.colorScheme.onPrimary,
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
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                WindowViewContent(
                    contextMenuItems = contextMenuItems,
                    stream = uiState.stream,
                    window = window,
                    highlights = uiState.highlights,
                    presets = uiState.presets,
                    defaultStyle = uiState.defaultStyle,
                    onActionClicked = onActionClicked,
                    selectable = uiState.allowSelection,
                )
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
    contextMenuItems: () -> List<ContextMenuItem>,
    stream: ComposeTextStream,
    window: Window?,
    highlights: List<ViewHighlight>,
    presets: Map<String, StyleDefinition>,
    defaultStyle: StyleDefinition,
    selectable: Boolean,
    onActionClicked: (String) -> Unit
) {
    val backgroundColor = (window?.backgroundColor?.specifiedOrNull() ?: defaultStyle.backgroundColor).toColor()
    val textColor = (window?.textColor?.specifiedOrNull() ?: defaultStyle.textColor).toColor()
    val fontFamily = (window?.fontFamily ?: defaultStyle.fontFamily)?.let { fontFamilyMap[it] }
    val fontSize = (window?.fontSize ?: defaultStyle.fontSize)?.sp ?: defaultFontSize

    val snapshot by stream.snapshot.collectAsState()
    val lines = snapshot.lines
    val components = snapshot.components

    Box(Modifier.background(backgroundColor).padding(vertical = 4.dp)) {
        val scrollState = rememberLazyListState()

        // TODO: reimplement this in a way that passes clicks through to clickable text
        SelectionContainer(items = contextMenuItems, enabled = selectable) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = LocalScrollbarStyle.current.thickness),
                state = scrollState
            ) {
                items(
                    items = lines,
                    key = { it.serialNumber }
                ) { streamLine ->
                    val line = streamLine.toWindowLine(
                        highlights = highlights,
                        presets = presets,
                        components = components,
                    )
                    if (line != null) {
                        Box(
                            modifier = Modifier.fillParentMaxWidth()
                                .background(line.entireLineStyle?.backgroundColor?.toColor() ?: Color.Unspecified)
                                .padding(horizontal = 4.dp)
                        ) {
                            ClickableText(
                                text = line.text,
                                style = TextStyle(
                                    color = textColor,
                                    fontFamily = fontFamily,
                                    fontSize = fontSize
                                ),
                            ) { offset ->
                                println("handling click: $offset")
                                line.text.getStringAnnotations(start = offset, end = offset)
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

        // This probably shouldn't cause a recomposition
        var prevLastSerial by remember { mutableStateOf(-1L) }
        val lastSerial = lines.lastOrNull()?.serialNumber
        LaunchedEffect(lastSerial) {
            if (lastSerial != null) {
                // If we're at the spot we last scrolled to
                val lastVisibleSerial = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                    ?.let { lines.getOrNull(it)?.serialNumber }
                    ?: -1L
                if ((lastVisibleSerial >= prevLastSerial || lastVisibleSerial == -1L) && lines.lastIndex > 0) { // scroll to the end if we were at the end
                    scrollState.scrollToItem(lines.lastIndex)
                }
                // remember the last serial
                prevLastSerial = lastSerial
            }
        }
    }
}

@Composable
fun SelectionContainer(items: () -> List<ContextMenuItem>, enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) {
        SelectionContainer(content = content)
    } else {
        ContextMenuArea(items) {
            content()
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
                editColor?.second?.invoke(color)
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