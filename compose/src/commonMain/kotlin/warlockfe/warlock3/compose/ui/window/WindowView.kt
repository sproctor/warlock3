package warlockfe.warlock3.compose.ui.window

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import io.github.oikvpqya.compose.fastscroller.ThumbStyle
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.components.ScrollableLazyColumn
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
import warlockfe.warlock3.core.window.StreamImageLine
import warlockfe.warlock3.core.window.StreamTextLine
import warlockfe.warlock3.core.window.Window
import warlockfe.warlock3.core.window.WindowLocation
import warlockfe.warlock3.stormfront.util.CompiledAlteration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

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
    val scrollState = rememberLazyListState()

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
                        style = MaterialTheme.typography.titleSmall,
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
                        alterations = uiState.alterations,
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
                        scrollState.scrollToItem(scrollState.layoutInfo.totalItemsCount - 1)
                    }

                    ScrollEvent.BUFFER_START -> {
                        scrollState.scrollToItem(0)
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

@OptIn(ExperimentalTime::class)
@Composable
private fun WindowViewContent(
    stream: ComposeTextStream,
    scrollState: LazyListState,
    window: Window?,
    highlights: List<ViewHighlight>,
    alterations: List<CompiledAlteration>,
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
        ScrollableLazyColumn(
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
            items(
                items = lines,
                key = { it.serialNumber }
            ) { streamLine ->
                when (streamLine) {
                    is StreamTextLine -> {
                        val line = streamLine.toWindowLine(
                            highlights = highlights,
                            presets = presets,
                            components = components,
                            alterations = alterations,
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
                                val text = if (window?.showTimestamps == true) {
                                    line.text + AnnotatedString(" [${line.timestamp.toTimeString()}]")
                                } else {
                                    line.text
                                }

                                BasicText(
                                    text = text,
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

                    is StreamImageLine -> {
                        Box(Modifier.height(50.dp).fillMaxWidth()) {
                            val painter = rememberAsyncImagePainter(
                                ImageRequest.Builder(LocalPlatformContext.current)
                                    .data(streamLine.url)
                                    .size(Size.ORIGINAL)
                                    .build()
                            )
                            val painterState by painter.state.collectAsState()
                            when (val state = painterState) {
                                is AsyncImagePainter.State.Success -> {
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val hovered by interactionSource.collectIsHoveredAsState()
                                    LocalLogger.current.debug { "Hovered: $hovered" }
                                    val height = if (hovered) {
                                        with(LocalDensity.current) {
                                            max(state.result.image.height.toDp(), 50.dp)
                                        }
                                    } else {
                                        50.dp
                                    }
                                    Image(
                                        modifier = Modifier
                                            .hoverable(interactionSource)
                                            .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                                            .animateContentSize()
                                            .height(height)
                                        ,
                                        painter = painter,
                                        contentDescription = null,
                                    )
                                }

                                else -> {}
                            }
                        }
                    }
                }
            }
        }

        var sticky by remember { mutableStateOf(true) }
        val lastSerial = lines.lastOrNull()?.serialNumber
        LaunchedEffect(lastSerial) {
            if (lastSerial != null && sticky) {
                lines.lastIndex.takeIf { it > -1 }?.let { index ->
                    scrollState.scrollToItem(index)
                }
            }
        }
        LaunchedEffect(scrollState.lastScrolledBackward, scrollState.canScrollForward) {
            // If the user scrolled back and they can scroll forward, stop being sticky
            // if the window can't be scrolled forward, be sticky again
            if (scrollState.lastScrolledBackward && scrollState.canScrollForward) {
                sticky = false
            } else if (!scrollState.canScrollForward) {
                sticky = true
            }
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@OptIn(ExperimentalTime::class)
private fun Instant.toTimeString(timeZone: TimeZone = TimeZone.getDefault()): String {
    val zonedDateTime = toJavaInstant().atZone(timeZone.toZoneId())
    return timeFormatter.format(zonedDateTime)
}