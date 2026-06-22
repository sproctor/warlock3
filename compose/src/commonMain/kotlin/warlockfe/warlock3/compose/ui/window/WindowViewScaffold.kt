package warlockfe.warlock3.compose.ui.window

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import warlockfe.warlock3.compose.util.ClearContextMenuItemKey
import warlockfe.warlock3.compose.util.CloseContextMenuItemKey
import warlockfe.warlock3.compose.util.SettingsContextMenuItemKey
import warlockfe.warlock3.compose.util.addItem
import warlockfe.warlock3.compose.util.createFontFamily
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.macro.ScrollEvent
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.ClientBackgroundImage
import warlockfe.warlock3.core.window.WindowLocation

/**
 * Platform-agnostic window view. Holds the structure shared by the desktop and mobile clients (the
 * frame, header, stream/dialog dispatch, the text/image render loop, and keyboard scroll handling).
 * The pieces that genuinely differ between toolkits (the surrounding surface, the header chrome, the
 * scrollbar, the action context menu, the settings dialog, and dialog content) are supplied by the
 * caller as slots so each platform can render them with its own component library.
 */
@Composable
internal fun WindowViewScaffold(
    uiState: WindowUiState,
    location: WindowLocation,
    defaultStyle: StyleDefinition,
    isSelected: Boolean,
    openWindows: List<String>,
    menuData: WarlockMenuData?,
    onActionClick: (WarlockAction) -> Int?,
    onCloseClick: () -> Unit,
    onSelect: () -> Unit,
    clearStream: () -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    defaultFontSize: TextUnit,
    surface: @Composable (modifier: Modifier, content: @Composable () -> Unit) -> Unit,
    header: @Composable (title: String, onSettingsClick: () -> Unit) -> Unit,
    listContainer: @Composable (scrollState: LazyListState, content: @Composable () -> Unit) -> Unit,
    actionContextMenu: @Composable (offset: Offset?, menuData: WarlockMenuData, onDismiss: () -> Unit) -> Unit,
    settingsDialog: @Composable (onCloseRequest: () -> Unit) -> Unit,
    dialogContent: @Composable (data: DialogWindowData, style: StyleDefinition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val window by uiState.windowInfo
    var showWindowSettingsDialog by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()

    val title = (window?.title ?: uiState.name) + (window?.subtitle ?: "")
    var viewportHeight by remember { mutableIntStateOf(0) }
    val currentOnSelect by rememberUpdatedState(onSelect)

    surface(
        modifier
            .padding(2.dp)
            .onLayoutRectChanged { bounds ->
                viewportHeight = bounds.height
            }.onFocusChanged { focusState ->
                if (focusState.hasFocus) {
                    onSelect()
                }
            }.pointerInput(Unit) {
                // Select this window on any press inside it, not only when the text grabs focus
                // (via the selection container) and bubbles up to onFocusChanged. Observe in the
                // Initial pass without consuming so text selection, links, and header buttons still
                // receive the event.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            currentOnSelect()
                        }
                    }
                }
            }.semantics {
                paneTitle = title
            },
    ) {
        Column {
            header(title) { showWindowSettingsDialog = true }

            when (val data = uiState.data) {
                is StreamWindowData -> {
                    WindowViewContent(
                        modifier =
                            Modifier.addTextContextMenuOptions(
                                windowLocation = location,
                                showSettingsDialog = { showWindowSettingsDialog = true },
                                onClearClick = clearStream,
                                onCloseClick = onCloseClick,
                            ),
                        windowName = uiState.name,
                        stream = data.stream,
                        scrollState = scrollState,
                        style = uiState.style.mergeWith(defaultStyle),
                        backgroundImage = window?.backgroundImage,
                        openWindows = openWindows,
                        menuData = menuData,
                        onActionClick = onActionClick,
                        defaultFontSize = defaultFontSize,
                        listContainer = listContainer,
                        actionContextMenu = actionContextMenu,
                    )
                }

                is DialogWindowData -> {
                    dialogContent(data, uiState.style.mergeWith(defaultStyle))
                }

                else -> {
                    // Loading
                }
            }
        }
    }

    if (showWindowSettingsDialog) {
        settingsDialog { showWindowSettingsDialog = false }
    }

    val currentHandledScrollEvent by rememberUpdatedState(handledScrollEvent)
    LaunchedEffect(scrollEvents) {
        if (isSelected) {
            val event = scrollEvents.firstOrNull()
            if (event != null) {
                when (event) {
                    ScrollEvent.PAGE_UP -> {
                        scrollState.scrollBy(-viewportHeight.toFloat())
                    }

                    ScrollEvent.PAGE_DOWN -> {
                        scrollState.scrollBy(viewportHeight.toFloat())
                    }

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
                currentHandledScrollEvent(event)
            }
        }
    }
}

@Composable
private fun WindowViewContent(
    windowName: String,
    stream: ComposeTextStream,
    scrollState: LazyListState,
    style: StyleDefinition,
    backgroundImage: ClientBackgroundImage?,
    openWindows: List<String>,
    menuData: WarlockMenuData?,
    onActionClick: (WarlockAction) -> Int?,
    defaultFontSize: TextUnit,
    listContainer: @Composable (scrollState: LazyListState, content: @Composable () -> Unit) -> Unit,
    actionContextMenu: @Composable (offset: Offset?, menuData: WarlockMenuData, onDismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = style.backgroundColor.toColor()
    val textColor = style.textColor.toColor()
    val fontFamily = style.fontFamily?.let { createFontFamily(it) }
    val fontSize = style.fontSize?.sp ?: defaultFontSize
    val fontWeight = style.fontWeight?.let { FontWeight(it) }

    val lines = stream.lines.collectAsState(emptyList()).value

    val findController = LocalWindowFindController.current
    val findUiState by findController.state.collectAsState()
    val activeFind = findUiState?.takeIf { it.windowName == windowName }

    var clickOffset by remember { mutableStateOf<Offset?>(null) }
    var openMenuId by remember { mutableStateOf<Int?>(null) }

    val currentOnActionClick by rememberUpdatedState(onActionClick)
    DisposableEffect(stream) {
        stream.actionHandler = { action ->
            Logger.d { "action clicked: $action" }
            openMenuId = currentOnActionClick(action)
        }
        onDispose {
            stream.actionHandler = null
        }
    }

    Box(modifier.fillMaxSize()) {
        SelectionContainer {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .background(backgroundColor),
            ) {
                backgroundImage?.takeIf { it.image.isNotBlank() }?.let { image ->
                    WindowBackgroundImage(
                        backgroundImage = image,
                        width = maxWidth,
                        height = maxHeight,
                        modifier = Modifier.align(image.backgroundAlignment()),
                    )
                }
                listContainer(scrollState) {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp)
                                .semantics {
                                    isTraversalGroup = true
                                    liveRegion = LiveRegionMode.Polite
                                },
                        state = scrollState,
                    ) {
                        items(
                            count = lines.size,
                            key = { index -> lines[index].serialNumber },
                        ) { index ->
                            when (val line = lines[index]) {
                                is StreamTextLine -> {
                                    if (line.text != null &&
                                        line.isShowing(openWindows) &&
                                        (!line.isPrompt || !lines.isPreviousPrompt(index, openWindows))
                                    ) {
                                        var positionInParent by remember { mutableStateOf(Offset.Zero) }
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .onGloballyPositioned {
                                                        positionInParent = it.positionInParent()
                                                    }.background(
                                                        line.entireLineStyle?.backgroundColor?.toColor()
                                                            ?: Color.Unspecified,
                                                    ).padding(start = 4.dp, end = 8.dp),
                                        ) {
                                            BasicText(
                                                modifier =
                                                    Modifier
                                                        .pointerInput(Unit) {
                                                            awaitPointerEventScope {
                                                                while (true) {
                                                                    val event = awaitPointerEvent()
                                                                    if (event.type == PointerEventType.Press) {
                                                                        Logger.d { "Click: $event" }
                                                                        clickOffset =
                                                                            event.changes
                                                                                .firstOrNull()
                                                                                ?.position
                                                                                ?.let { it + positionInParent }
                                                                    }
                                                                }
                                                            }
                                                        },
                                                text =
                                                    if (activeFind != null) {
                                                        val ranges =
                                                            activeFind.matchRangesBySerial[line.serialNumber]
                                                        if (ranges != null) {
                                                            line.text.withFindHighlight(
                                                                ranges = ranges,
                                                                currentRange =
                                                                    if (line.serialNumber == activeFind.currentSerial) {
                                                                        activeFind.currentRange
                                                                    } else {
                                                                        null
                                                                    },
                                                            )
                                                        } else {
                                                            line.text
                                                        }
                                                    } else {
                                                        line.text
                                                    },
                                                style =
                                                    TextStyle(
                                                        color = textColor,
                                                        fontFamily = fontFamily,
                                                        fontSize = fontSize,
                                                        fontWeight = fontWeight,
                                                    ),
                                            )
                                        }
                                    }
                                }

                                is StreamImageLine -> {
                                    val defaultHeight = 80.dp
                                    Box(Modifier.height(defaultHeight).fillMaxWidth().zIndex(1f)) {
                                        val painter =
                                            rememberAsyncImagePainter(
                                                ImageRequest
                                                    .Builder(LocalPlatformContext.current)
                                                    .data(line.url)
                                                    .size(Size.ORIGINAL)
                                                    .build(),
                                            )
                                        val painterState by painter.state.collectAsState()
                                        when (val state = painterState) {
                                            is AsyncImagePainter.State.Success -> {
                                                val interactionSource = remember { MutableInteractionSource() }
                                                val hovered by interactionSource.collectIsHoveredAsState()
                                                Logger.d { "Hovered: $hovered" }
                                                val height =
                                                    if (hovered) {
                                                        with(LocalDensity.current) {
                                                            max(
                                                                state.result.image.height
                                                                    .toDp(),
                                                                defaultHeight,
                                                            )
                                                        }
                                                    } else {
                                                        defaultHeight
                                                    }
                                                Image(
                                                    modifier =
                                                        Modifier
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
                }
            }
        }
        if (activeFind != null) {
            FindOverlay(
                state = activeFind,
                onQueryChange = findController::setQuery,
                onNext = findController::next,
                onPrevious = findController::previous,
                onClose = findController::close,
                onFocusChanged = findController::setFocused,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }
    }

    if (menuData != null && menuData.id == openMenuId) {
        actionContextMenu(clickOffset, menuData) { openMenuId = null }
    }

    // Jump the current find match into view. While find is active, suppress the sticky auto-scroll
    // below so newly-arriving lines don't yank the view away from the match.
    LaunchedEffect(activeFind?.currentSerial) {
        val serial = activeFind?.currentSerial
        if (serial != null) {
            val index = lines.indexOfFirst { it.serialNumber == serial }
            if (index >= 0) {
                scrollState.scrollToItem(index)
            }
        }
    }

    var sticky by remember { mutableStateOf(true) }
    val lastSerial = lines.lastOrNull()?.serialNumber
    LaunchedEffect(lastSerial) {
        if (lastSerial != null && sticky && activeFind == null) {
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
internal fun Modifier.addTextContextMenuOptions(
    windowLocation: WindowLocation,
    showSettingsDialog: () -> Unit,
    onClearClick: () -> Unit,
    onCloseClick: () -> Unit,
): Modifier =
    this.appendTextContextMenuComponents {
        separator()
        addItem(key = SettingsContextMenuItemKey, label = "Window settings ...") {
            showSettingsDialog()
            close()
        }
        addItem(key = ClearContextMenuItemKey, label = "Clear window") {
            onClearClick()
            close()
        }
        if (windowLocation != WindowLocation.MAIN) {
            addItem(key = CloseContextMenuItemKey, label = "Hide window") {
                onCloseClick()
                close()
            }
        }
    }
