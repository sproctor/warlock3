package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.oikvpqya.compose.fastscroller.ThumbStyle
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.components.defaultScrollbarStyle
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.ui.components.DialogContent
import warlockfe.warlock3.compose.ui.game.toWindowLine
import warlockfe.warlock3.compose.ui.settings.WindowSettingsDialog
import warlockfe.warlock3.compose.util.LocalLogger
import warlockfe.warlock3.compose.util.createFontFamily
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.specifiedOrNull
import warlockfe.warlock3.core.window.Window
import warlockfe.warlock3.core.window.WindowLocation

@Composable
fun WindowView(
    modifier: Modifier,
    uiState: WindowUiState,
    isSelected: Boolean,
    onActionClicked: (WarlockAction) -> Unit,
    onMoveClicked: (WindowLocation) -> Unit,
    onMoveTowardsStart: (() -> Unit)?,
    onMoveTowardsEnd: (() -> Unit)?,
    onCloseClicked: () -> Unit,
    saveStyle: (StyleDefinition) -> Unit,
    onSelected: () -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    clearStream: () -> Unit,
) {
    val window = uiState.window
    var showWindowSettingsDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val title = (uiState.window?.title ?: "") + (uiState.window?.subtitle ?: "")
    var viewportHeight by mutableIntStateOf(0)
    Surface(
        modifier.padding(2.dp)
            .onLayoutRectChanged { bounds ->
                viewportHeight = bounds.height
            }
            .clickable(interactionSource = null, indication = null) {
                onSelected()
            }
            .semantics {
                paneTitle = title
            },
        shape = MaterialTheme.shapes.extraSmall,
        shadowElevation = 2.dp,
        border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            Row(
                Modifier
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Box {
                    var showDropdown by remember { mutableStateOf(false) }
                    IconButton(
                        modifier = Modifier.size(24.dp),
                        onClick = { showDropdown = !showDropdown }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
                        clearStream = clearStream,
                    )
                }
                if (uiState.window?.location != WindowLocation.MAIN) {
                    IconButton(
                        modifier = Modifier.size(24.dp),
                        onClick = onCloseClicked
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            when (uiState) {
                is StreamWindowUiState ->
                    WindowViewContent(
                        stream = uiState.stream,
                        scrollState = scrollState,
                        window = window,
                        highlights = uiState.highlights,
                        presets = uiState.presets,
                        defaultStyle = uiState.defaultStyle,
                        onActionClicked = onActionClicked,
                    )

                is DialogWindowUiState ->
                    ScrollableColumn(Modifier.background(uiState.style.backgroundColor.toColor())) {
                        DialogContent(
                            dataObjects = uiState.dialogData,
                            modifier = Modifier.fillMaxSize()
                                .padding(8.dp),
                            executeCommand = { command ->
                                onActionClicked(WarlockAction.SendCommand(command))
                            },
                        )
                    }
            }
        }
    }

    if (showWindowSettingsDialog && window != null && uiState is StreamWindowUiState) {
        WindowSettingsDialog(
            onCloseRequest = { showWindowSettingsDialog = false },
            style = StyleDefinition(
                textColor = window.textColor,
                backgroundColor = window.backgroundColor,
                fontFamily = window.fontFamily,
                fontSize = window.fontSize
            ),
            defaultStyle = uiState.defaultStyle,
            saveStyle = saveStyle,
        )
    }

    LaunchedEffect(scrollEvents) {
        if (isSelected) {
            val event = scrollEvents.firstOrNull()
            if (event != null) {
                when (event) {
                    ScrollEvent.PAGE_UP -> scrollState.scrollBy(-viewportHeight.toFloat())
                    ScrollEvent.PAGE_DOWN -> scrollState.scrollBy(viewportHeight.toFloat())

                    ScrollEvent.LINE_UP -> {
                        scrollState.scrollBy(-20f)
                    }

                    ScrollEvent.LINE_DOWN -> {
                        scrollState.scrollBy(20f)
                    }

                    ScrollEvent.BUFFER_END -> {
                        scrollState.scrollTo(0)
                    }

                    ScrollEvent.BUFFER_START -> {
                        scrollState.scrollTo(scrollState.maxValue)
                    }
                }
                handledScrollEvent(event)
            }
        }
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
    clearStream: () -> Unit,
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
        DropdownMenuItem(
            onClick = {
                clearStream()
                onDismissRequest()
            },
            text = { Text("Clear window") },
        )
        if (location != null && location != WindowLocation.MAIN) {
            WindowLocation.entries.forEach { otherLocation ->
                if (location != otherLocation && otherLocation != WindowLocation.MAIN) {
                    DropdownMenuItem(
                        text = { Text("Move to ${otherLocation.value.lowercase()} slot") },
                        onClick = {
                            onMoveClicked(otherLocation)
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
    scrollState: ScrollState,
    window: Window?,
    highlights: List<ViewHighlight>,
    presets: Map<String, StyleDefinition>,
    defaultStyle: StyleDefinition,
    onActionClicked: (WarlockAction) -> Unit
) {
    val logger = LocalLogger.current

    val backgroundColor = (window?.backgroundColor?.specifiedOrNull() ?: defaultStyle.backgroundColor).toColor()
    val textColor = (window?.textColor?.specifiedOrNull() ?: defaultStyle.textColor).toColor()
    val fontFamily = (window?.fontFamily ?: defaultStyle.fontFamily)?.let { createFontFamily(it) }
    val fontSize = (window?.fontSize ?: defaultStyle.fontSize)?.sp ?: MaterialTheme.typography.bodyMedium.fontSize

    val lines = stream.lines
    val components = stream.components

    SelectionContainer {
        ScrollableColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(vertical = 4.dp)
                .semantics {
                    isTraversalGroup = true
                    liveRegion = LiveRegionMode.Polite
                },
            state = scrollState,
            scrollbarStyle = defaultScrollbarStyle(
                thumbStyle = ThumbStyle(
                    shape = RoundedCornerShape(4.dp),
                    unhoverColor = textColor.copy(alpha = 0.2f),
                    hoverColor = textColor,
                )
            )
        ) {
            lines.forEach { streamLine ->
                val line = streamLine.toWindowLine(
                    highlights = highlights,
                    presets = presets,
                    components = components,
                ) { action ->
                    logger.debug { "action clicked: $action" }
                    onActionClicked(action)
                }
                // FIXME: if line is null, I think we can screw up the scrolling
                if (line != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
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
                        // Add newlines in selected text
                        BasicText(text = "\n", modifier = Modifier.size(0.dp))
                    }
                }
            }

            // This probably shouldn't cause a recomposition
            var prevLastSerial by remember { mutableStateOf(-1L) }
            val lastSerial = lines.lastOrNull()?.serialNumber
            var stickToEnd by remember { mutableStateOf(true) }
            LaunchedEffect(scrollState.value) {
                if (prevLastSerial == lastSerial) {
                    stickToEnd = scrollState.value == scrollState.maxValue
                }
            }
            LaunchedEffect(lastSerial) {
                if (lastSerial != null) {
                    if (stickToEnd) {
                        scrollState.scrollTo(scrollState.maxValue)
                    }
                    // remember the last serial
                    prevLastSerial = lastSerial
                }
            }
        }
    }
}