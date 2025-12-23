package warlockfe.warlock3.compose.ui.window

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import io.github.oikvpqya.compose.fastscroller.VerticalScrollbar
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.components.defaultScrollbarStyle
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.arrow_right
import warlockfe.warlock3.compose.generated.resources.close
import warlockfe.warlock3.compose.generated.resources.settings_filled
import warlockfe.warlock3.compose.ui.settings.WindowSettingsDialog
import warlockfe.warlock3.compose.util.ClearContextMenuItemKey
import warlockfe.warlock3.compose.util.CloseContextMenuItemKey
import warlockfe.warlock3.compose.util.LocalLogger
import warlockfe.warlock3.compose.util.SettingsContextMenuItemKey
import warlockfe.warlock3.compose.util.addItem
import warlockfe.warlock3.compose.util.createFontFamily
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.specifiedOrNull
import warlockfe.warlock3.core.window.Window
import warlockfe.warlock3.core.window.WindowLocation
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WindowView(
    modifier: Modifier,
    headerModifier: Modifier,
    uiState: WindowUiState,
    isSelected: Boolean,
    menuData: WarlockMenuData?,
    onActionClicked: (WarlockAction) -> Int?,
    onMoveClicked: (WindowLocation) -> Unit,
    onCloseClicked: () -> Unit,
    saveStyle: (StyleDefinition) -> Unit,
    onSelected: () -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    clearStream: () -> Unit,
) {
    val window = uiState.window
    val windowLocation = window.location ?: return // Can't display anything without a location
    var showWindowSettingsDialog by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()

    val title = window.title + (window.subtitle ?: "")
    var viewportHeight by remember { mutableIntStateOf(0) }
    Surface(
        modifier.padding(2.dp)
            .onLayoutRectChanged { bounds ->
                viewportHeight = bounds.height
            }
            .onFocusChanged { focusState ->
                if (focusState.hasFocus) {
                    onSelected()
                }
            }
            .semantics {
                paneTitle = title
            },
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            WindowHeader(
                modifier = headerModifier
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .fillMaxWidth(),
                title = {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
                location = windowLocation,
                isSelected = isSelected,
                onSettingsClicked = { showWindowSettingsDialog = true },
                onClearClicked = clearStream,
                onCloseClicked = onCloseClicked,
                onMoveClicked = onMoveClicked,
            )

            when (uiState) {
                is StreamWindowUiState ->
                    WindowViewContent(
                        modifier = Modifier.addTextContextMenuOptions(
                            windowLocation = windowLocation,
                            showSettingsDialog = { showWindowSettingsDialog = true },
                            onMoveClicked = onMoveClicked,
                            onClearClicked = clearStream,
                            onCloseClicked = onCloseClicked,
                        ),
                        stream = uiState.stream,
                        scrollState = scrollState,
                        window = window,
                        defaultStyle = uiState.defaultStyle,
                        menuData = menuData,
                        onActionClicked = onActionClicked,
                    )

                is DialogWindowUiState -> {
                    ScrollableColumn(
                        Modifier.fillMaxSize()
                            .background(
                                (window.style.backgroundColor.specifiedOrNull()
                                    ?: uiState.defaultStyle.backgroundColor).toColor()
                            )
                    ) {
                        val dataObjects by uiState.dialogData.objects.collectAsState()
                        DialogContent(
                            dataObjects = dataObjects,
                            modifier = Modifier.padding(8.dp),
                            executeCommand = { command ->
                                onActionClicked(WarlockAction.SendCommand(command))
                            },
                            style = window.style.mergeWith(uiState.defaultStyle),
                        )
                    }
                }
            }
        }
    }

    if (showWindowSettingsDialog) {
        WindowSettingsDialog(
            onCloseRequest = { showWindowSettingsDialog = false },
            style = window.style,
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
private fun Modifier.addTextContextMenuOptions(
    windowLocation: WindowLocation,
    showSettingsDialog: () -> Unit,
    onMoveClicked: (WindowLocation) -> Unit,
    onClearClicked: () -> Unit,
    onCloseClicked: () -> Unit,
): Modifier {
    return this.appendTextContextMenuComponents {
        separator()
        addItem(key = SettingsContextMenuItemKey, label = "Window settings ...") {
            showSettingsDialog()
            close()
        }
        addItem(key = ClearContextMenuItemKey, label = "Clear window") {
            onClearClicked()
            close()
        }
        addItem(key = CloseContextMenuItemKey, label = "Hide window") {
            onCloseClicked()
            close()
        }
        if (windowLocation != WindowLocation.MAIN) {
            separator()
            WindowLocation.entries.forEach { otherLocation ->
                if (windowLocation != otherLocation && otherLocation != WindowLocation.MAIN) {
                    addItem(key = otherLocation, label = "Move to ${otherLocation.value.lowercase()} slot") {
                        onMoveClicked(otherLocation)
                        close()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class, ExperimentalComposeUiApi::class)
@Composable
private fun WindowViewContent(
    modifier: Modifier,
    stream: ComposeTextStream,
    scrollState: LazyListState,
    window: Window?,
    defaultStyle: StyleDefinition,
    menuData: WarlockMenuData?,
    onActionClicked: (WarlockAction) -> Int?
) {
    val logger = LocalLogger.current

    val style = window?.style?.mergeWith(defaultStyle) ?: defaultStyle

    val backgroundColor = style.backgroundColor.toColor()
    val textColor = style.textColor.toColor()
    val fontFamily = style.fontFamily?.let { createFontFamily(it) }
    val fontSize = style.fontSize?.sp ?: MaterialTheme.typography.bodyMedium.fontSize

    val lines by stream.lines.collectAsState(emptyList())

    var clickOffset by remember { mutableStateOf<Offset?>(null) }
    var openMenuId by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(stream) {
        stream.actionHandler = { action ->
            logger.debug { "action clicked: $action" }
            openMenuId = onActionClicked(action)
        }
        onDispose {
            stream.actionHandler = null
        }
    }

    SelectionContainer(modifier = modifier) {
        Box {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .padding(vertical = 4.dp)
                    .semantics {
                        isTraversalGroup = true
                        liveRegion = LiveRegionMode.Polite
                    },
                state = scrollState,
            ) {
                items(
                    items = lines,
                    key = { it.serialNumber }
                ) { line ->
                    when (line) {
                        is StreamTextLine -> {
                            if (line.text != null) {
                                var positionInParent by remember { mutableStateOf(Offset.Zero) }
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .onGloballyPositioned {
                                            positionInParent = it.positionInParent()
                                        }
                                        .background(
                                            line.entireLineStyle?.backgroundColor?.toColor()
                                                ?: Color.Unspecified
                                        )
                                        .padding(horizontal = 4.dp)
                                ) {
                                    BasicText(
                                        modifier = Modifier
                                            .pointerInput(Unit) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        if (event.type == PointerEventType.Press) {
                                                            logger.debug { "Click: $event" }
                                                            clickOffset = event.changes.firstOrNull()
                                                                ?.position?.let { it + positionInParent }
                                                        }
                                                    }
                                                }
                                            },
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

                        is StreamImageLine -> {
                            val defaultHeight = 80.dp
                            Box(Modifier.height(defaultHeight).fillMaxWidth().zIndex(1f)) {
                                val painter = rememberAsyncImagePainter(
                                    ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(line.url)
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
                                                max(state.result.image.height.toDp(), defaultHeight)
                                            }
                                        } else {
                                            defaultHeight
                                        }
                                        Image(
                                            modifier = Modifier
                                                .hoverable(interactionSource)
                                                .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                                                .animateContentSize()
                                                .height(height),
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
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                style = defaultScrollbarStyle(),
                modifier = Modifier.align(Alignment.CenterEnd)
                    .fillMaxHeight(),
            )
        }
    }

    if (menuData != null && menuData.id == openMenuId) {
        ActionContextMenu(
            offset = clickOffset,
            menuData = menuData,
            onDismiss = { openMenuId = null },
        )
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

@Composable
private fun ActionContextMenu(
    offset: Offset?,
    menuData: WarlockMenuData,
    onDismiss: () -> Unit,
) {
    val logger = LocalLogger.current
    val scope = rememberCoroutineScope()
    DropdownMenu(
        offset = offset?.let { with(LocalDensity.current) { DpOffset(it.x.toDp(), it.y.toDp()) } } ?: DpOffset.Zero,
        expanded = true,
        onDismissRequest = onDismiss,
    ) {
        val groups = menuData.items.groupBy { it.category.split('-').first() }
        val categories = groups.keys.sorted()
        categories.forEach { category ->
            val items = groups[category]!!
            if (!category.contains('_')) {
                items.forEach { item ->
                    logger.debug { "Menu item: $item" }
                    DropdownMenuItem(
                        text = {
                            Text(item.label)
                        },
                        onClick = {
                            scope.launch {
                                item.action()
                                onDismiss()
                            }
                        }
                    )
                }
            } else {
                var expanded by remember(category) { mutableStateOf(false) }
                DropdownMenuItem(
                    text = {
                        Text(category.split('_').getOrNull(1) ?: "Unknown")
                    },
                    onClick = { expanded = true },
                    trailingIcon = {
                        Icon(painter = painterResource(Res.drawable.arrow_right), contentDescription = "expandable")
                    }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    val subgroups = items.groupBy { it.category.split('-').getOrNull(1) }
                    subgroups[null]?.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Text(item.label)
                            },
                            onClick = {
                                scope.launch {
                                    item.action()
                                    onDismiss()
                                }
                            }
                        )
                    }
                    val subcatories = subgroups.keys.filterNotNull().sorted()
                    subcatories.forEach { category ->
                        var expanded by remember(category) { mutableStateOf(false) }
                        DropdownMenuItem(
                            text = {
                                Text(category)
                            },
                            onClick = { expanded = true },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(Res.drawable.arrow_right),
                                    contentDescription = "expandable"
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            subgroups[category]?.forEach { item ->
                                DropdownMenuItem(
                                    text = {
                                        Text(item.label)
                                    },
                                    onClick = {
                                        scope.launch {
                                            item.action()
                                            onDismiss()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
