package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import warlockfe.warlock3.compose.components.ScrollableLazyColumn
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.ui.game.toWindowLine
import warlockfe.warlock3.compose.ui.settings.WindowSettingsDialog
import warlockfe.warlock3.compose.ui.settings.fontFamilyMap
import warlockfe.warlock3.compose.util.defaultFontSize
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.specifiedOrNull
import warlockfe.warlock3.core.window.Window
import warlockfe.warlock3.core.window.WindowLocation

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

    Surface(
        modifier.padding(2.dp),
        shape = MaterialTheme.shapes.extraSmall,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            Row(
                Modifier.background(MaterialTheme.colorScheme.primary).fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    Text(
                        text = (uiState.window?.title ?: "") + (uiState.window?.subtitle ?: ""),
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                var showDropdown by remember { mutableStateOf(false) }
                IconButton(
                    modifier = Modifier.size(24.dp),
                    onClick = { showDropdown = true }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                WindowViewDropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false },
                    onSettingsClicked = { showWindowSettingsDialog = true },
                    onMoveClicked = onMoveClicked,
                    onMoveTowardsStart = onMoveTowardsStart,
                    onMoveTowardsEnd = onMoveTowardsEnd,
                    location = uiState.window?.location,
                )
                if (uiState.window?.location != WindowLocation.MAIN) {
                    IconButton(
                        modifier = Modifier.size(24.dp),
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
                stream = uiState.stream,
                window = window,
                highlights = uiState.highlights,
                presets = uiState.presets,
                defaultStyle = uiState.defaultStyle,
                onActionClicked = onActionClicked,
            )
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
private fun WindowViewDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onSettingsClicked: () -> Unit,
    location: WindowLocation?,
    onMoveClicked: (WindowLocation) -> Unit,
    onMoveTowardsStart: (() -> Unit)?,
    onMoveTowardsEnd: (() -> Unit)?,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            onClick = {
                onSettingsClicked()
                onDismissRequest()
            },
            text = { Text("Window Settings ...") }
        )
        if (location != null) {
            if (location != WindowLocation.MAIN) {
                if (location != WindowLocation.LEFT) {
                    DropdownMenuItem(
                        text = { Text("Move to left column") },
                        onClick = {
                            onMoveClicked(WindowLocation.LEFT)
                            onDismissRequest()
                        }
                    )
                }
                if (location != WindowLocation.TOP) {
                    DropdownMenuItem(
                        text = { Text("Move to center column") },
                        onClick = {
                            onMoveClicked(WindowLocation.TOP)
                            onDismissRequest()
                        }
                    )
                }
                if (location != WindowLocation.RIGHT) {
                    DropdownMenuItem(
                        text = { Text("Move to right column") },
                        onClick = {
                            onMoveClicked(WindowLocation.RIGHT)
                            onDismissRequest()
                        }
                    )
                }
            }
            if (onMoveTowardsStart != null) {
                DropdownMenuItem(
                    text = { Text("Move towards start") },
                    onClick = {
                        onMoveTowardsStart()
                        onDismissRequest()
                    },
                )
            }
            if (onMoveTowardsEnd != null) {
                DropdownMenuItem(
                    text = { Text("Move towards end") },
                    onClick = {
                        onMoveTowardsEnd()
                        onDismissRequest()
                    }
                )
            }
        }
    }
}

@Composable
private fun WindowViewContent(
    stream: ComposeTextStream,
    window: Window?,
    highlights: List<ViewHighlight>,
    presets: Map<String, StyleDefinition>,
    defaultStyle: StyleDefinition,
    onActionClicked: (String) -> Unit
) {
    val backgroundColor =
        (window?.backgroundColor?.specifiedOrNull() ?: defaultStyle.backgroundColor).toColor()
    val textColor = (window?.textColor?.specifiedOrNull() ?: defaultStyle.textColor).toColor()
    val fontFamily = (window?.fontFamily ?: defaultStyle.fontFamily)?.let { fontFamilyMap[it] }
    val fontSize = (window?.fontSize ?: defaultStyle.fontSize)?.sp ?: defaultFontSize

    val snapshot by stream.snapshot.collectAsState()
    val lines = snapshot.lines
    val components = snapshot.components

    // TODO: reimplement this in a way that passes clicks through to clickable text
    SelectionContainer {
        val scrollState = rememberLazyListState()
        ScrollableLazyColumn(
            modifier = Modifier.background(backgroundColor).padding(vertical = 4.dp),
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
                ) { action ->
                    println("action clicked: $action")
                    onActionClicked(action)
                }
                if (line != null) {
                    Box(
                        modifier = Modifier.fillParentMaxWidth()
                            .background(
                                line.entireLineStyle?.backgroundColor?.toColor()
                                    ?: Color.Unspecified
                            )
                            .padding(horizontal = 4.dp)
                    ) {
                        BasicText(
                            text = line.text,
                            style = TextStyle(
                                color = textColor,
                                fontFamily = fontFamily,
                                fontSize = fontSize
                            ),
                        )

                    }
                }
            }
        }

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
