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
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.compose.util.ClearContextMenuItemKey
import warlockfe.warlock3.compose.util.CloseContextMenuItemKey
import warlockfe.warlock3.compose.util.LocalBaseStyle
import warlockfe.warlock3.compose.util.SettingsContextMenuItemKey
import warlockfe.warlock3.compose.util.addItem
import warlockfe.warlock3.compose.util.createFontFamily
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.macro.ScrollEvent
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.toFontConfig
import warlockfe.warlock3.core.window.ClientBackgroundImage

/**
 * Platform-agnostic window view: the frame, header, stream/panel dispatch, render loop and scroll
 * handling shared by desktop and mobile. What differs between toolkits - surface, header chrome,
 * scrollbar, context menu, panel content - comes in as slots.
 */
@Composable
internal fun WindowViewScaffold(
    uiState: WindowUiState,
    canHide: Boolean,
    defaultStyle: StyleDefinition,
    isSelected: Boolean,
    openWindows: List<String>,
    menuData: WarlockMenuData?,
    onActionClick: (WarlockAction) -> Int?,
    onCloseClick: () -> Unit,
    onSelect: () -> Unit,
    onOpenWindowSettings: () -> Unit,
    clearStream: () -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    defaultFontSize: TextUnit,
    surface: @Composable (modifier: Modifier, content: @Composable () -> Unit) -> Unit,
    header: @Composable (title: String, onSettingsClick: () -> Unit) -> Unit,
    listContainer: @Composable (
        scrollState: LazyListState,
        heightModel: LazyListMeasuredScrollModel,
        content: @Composable () -> Unit,
    ) -> Unit,
    actionContextMenu: @Composable (offset: Offset?, menuData: WarlockMenuData, onDismiss: () -> Unit) -> Unit,
    panelContent: @Composable (
        data: PanelWindowData,
        style: StyleDefinition,
        font: FontConfig?,
        scale: Float?,
        onAction: (WarlockAction) -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val window by uiState.windowInfo
    val scrollState = rememberLazyListState()

    val title = (window?.title ?: uiState.name) + (window?.subtitle ?: "")
    var viewportHeight by remember { mutableIntStateOf(0) }
    val currentOnSelect by rememberUpdatedState(onSelect)

    surface(
        modifier
            .onLayoutRectChanged { bounds ->
                viewportHeight = bounds.height
            }.onFocusChanged { focusState ->
                if (focusState.hasFocus) {
                    onSelect()
                }
            }.pointerInput(Unit) {
                // Initial pass and never consumed, so text selection, links and header buttons
                // still see the press.
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
            header(title, onOpenWindowSettings)

            when (val data = uiState.data) {
                is StreamWindowData -> {
                    WindowViewContent(
                        modifier =
                            Modifier.addTextContextMenuOptions(
                                canHide = canHide,
                                showSettingsDialog = onOpenWindowSettings,
                                onClearClick = clearStream,
                                onCloseClick = onCloseClick,
                            ),
                        windowName = uiState.name,
                        stream = data.stream,
                        scrollState = scrollState,
                        style = uiState.style.mergeWith(defaultStyle),
                        font = uiState.font,
                        backgroundImage = window?.backgroundImage,
                        openWindows = openWindows,
                        menuData = menuData,
                        onActionClick = onActionClick,
                        defaultFontSize = defaultFontSize,
                        listContainer = listContainer,
                        actionContextMenu = actionContextMenu,
                    )
                }

                is PanelWindowData -> {
                    // Panel widgets open the same server-driven menu as stream links: the action
                    // returns a menu id, and the popup anchors where the press landed.
                    var clickOffset by remember { mutableStateOf<Offset?>(null) }
                    var openMenuId by remember { mutableStateOf<Int?>(null) }
                    val currentOnActionClick by rememberUpdatedState(onActionClick)
                    Box(
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press) {
                                        clickOffset = event.changes.firstOrNull()?.position
                                    }
                                }
                            }
                        },
                    ) {
                        panelContent(
                            data,
                            uiState.style.mergeWith(defaultStyle),
                            uiState.font,
                            uiState.scale,
                        ) { action ->
                            openMenuId = currentOnActionClick(action)
                        }
                        if (menuData != null && menuData.id == openMenuId) {
                            actionContextMenu(clickOffset, menuData) { openMenuId = null }
                        }
                    }
                }

                else -> {
                    // Loading
                }
            }
        }
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
    font: FontConfig?,
    backgroundImage: ClientBackgroundImage?,
    openWindows: List<String>,
    menuData: WarlockMenuData?,
    onActionClick: (WarlockAction) -> Int?,
    defaultFontSize: TextUnit,
    listContainer: @Composable (
        scrollState: LazyListState,
        heightModel: LazyListMeasuredScrollModel,
        content: @Composable () -> Unit,
    ) -> Unit,
    actionContextMenu: @Composable (offset: Offset?, menuData: WarlockMenuData, onDismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = style.backgroundColor.toColor()
    val textColor = style.textColor.toColor()
    // The window's base (normal) font: its per-window override if set, otherwise the character default.
    val effectiveFont = font ?: LocalBaseStyle.current.toFontConfig()
    val fontFamily = effectiveFont?.family?.let { createFontFamily(it) }
    val fontSize = effectiveFont?.size?.sp ?: defaultFontSize
    val fontWeight = effectiveFont?.weight?.let { FontWeight(it) }
    // The text style shared by the rendered row and the off-screen height measurer, so a measured
    // height matches what the row lays out. (Color does not affect height; the measurer ignores it.)
    val fontStyle = if (style.italic) FontStyle.Italic else null
    val textDecoration = if (style.underline) TextDecoration.Underline else null
    val rowTextStyle =
        remember(textColor, fontFamily, fontSize, fontWeight, fontStyle, textDecoration) {
            TextStyle(
                color = textColor,
                fontFamily = fontFamily,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textDecoration = textDecoration,
            )
        }

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
        // Keyed because the phone runs every tab through this one call site: a shared state would
        // carry a selection into the next window, anchored in rows that are already gone.
        val state = key(windowName) { rememberSelectionState() }
        // So {copy} and {selectall} can reach this window. The clipboard is a composition local.
        val selectionController = LocalWindowSelectionController.current
        val clipboard = LocalClipboard.current
        DisposableEffect(selectionController, windowName, state, clipboard) {
            selectionController.clipboard = clipboard
            selectionController.register(windowName, state)
            onDispose { selectionController.unregister(windowName) }
        }
        SelectionContainer(
            state = state,
        ) {
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
                // Kept across width and style changes: a stale height is approximately right and
                // self-corrects when the line renders again, which beats having no estimate.
                val measuredHeights = remember { mutableStateMapOf<Long, Int>() }
                // Serials of the rows composed right now, kept by the rows themselves for the
                // selection effect below. Not snapshot state: nothing composes from it.
                val composedSerials = remember { mutableSetOf<Long>() }
                val currentLines = rememberUpdatedState(lines)
                val currentOpenWindows = rememberUpdatedState(openWindows)
                val heightModel =
                    remember(measuredHeights) {
                        LazyListMeasuredScrollModel(
                            state = scrollState,
                            itemCount = { currentLines.value.size },
                            keyAt = { index -> currentLines.value.getOrNull(index)?.serialNumber },
                            isRendered = { index ->
                                val list = currentLines.value
                                index in list.indices &&
                                    list.rendersContent(index, currentOpenWindows.value)
                            },
                            measuredHeights = measuredHeights,
                        )
                    }
                // Drop heights for lines that have left the buffer: otherwise the map grows an
                // entry per line ever rendered, and stale values skew the scroll model's average.
                LaunchedEffect(measuredHeights) {
                    snapshotFlow { currentLines.value.firstOrNull()?.serialNumber }
                        .filterNotNull()
                        .collect { oldest ->
                            measuredHeights.keys
                                .filter { it < oldest }
                                .forEach { measuredHeights.remove(it) }
                        }
                }
                // A trim that takes a selected row leaves the SelectionManager holding a dead
                // selectable id, and the next drag throws out of getDirectionById. The buffer stays
                // bounded, so the selection gives way - but only when the trim reaches a composed
                // row, which is sound because Compose pins every row a selection touches. Read off
                // the stream rather than the composed list: this has to run before the composition
                // that disposes those rows, and a mirror written during composition is observable
                // only after it.
                LaunchedEffect(state, stream) {
                    stream.lines
                        .map { it.firstOrNull()?.serialNumber }
                        .distinctUntilChanged()
                        .drop(1)
                        .collect { oldest ->
                            // No oldest serial means the buffer emptied: every row went at once.
                            val cutoff = oldest ?: Long.MAX_VALUE
                            if (composedSerials.any { it < cutoff }) {
                                state.clear()
                            }
                        }
                }
                listContainer(scrollState, heightModel) {
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
                        // Compose never evicts LazyLayoutItemContentFactory's per-key cache, so
                        // ever-increasing serials retained an entry per line displayed - ~45k a
                        // minute under a heavy stream. Keys only have to be unique among the rows
                        // present at once; see [lazyItemKeyModulus].
                        val keyModulus = lazyItemKeyModulus(lines.serialSpan())
                        items(
                            count = lines.size,
                            key = { index -> lines[index].serialNumber % keyModulus },
                        ) { index ->
                            when (val line = lines[index]) {
                                is StreamTextLine -> {
                                    // The single source of truth for whether a line paints a row -
                                    // the scroll model keys its heights off it too - and it
                                    // guarantees non-null text when it does.
                                    if (lines.rendersContent(index, openWindows)) {
                                        val lineText = line.text ?: return@items
                                        DisposableEffect(line.serialNumber) {
                                            composedSerials.add(line.serialNumber)
                                            onDispose { composedSerials.remove(line.serialNumber) }
                                        }
                                        var positionInParent by remember { mutableStateOf(Offset.Zero) }
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    // Fires only on a size change.
                                                    .recordRowHeight(line.serialNumber, measuredHeights)
                                                    .onGloballyPositioned {
                                                        positionInParent = it.positionInParent()
                                                    }.background(
                                                        line.entireLineStyle?.backgroundColor?.toColor()
                                                            ?: Color.Unspecified,
                                                    ).padding(start = streamRowStartPadding, end = streamRowEndPadding),
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
                                                            lineText.withFindHighlight(
                                                                ranges = ranges,
                                                                currentRange =
                                                                    if (line.serialNumber == activeFind.currentSerial) {
                                                                        activeFind.currentRange
                                                                    } else {
                                                                        null
                                                                    },
                                                            )
                                                        } else {
                                                            lineText
                                                        }
                                                    } else {
                                                        lineText
                                                    },
                                                style = rowTextStyle,
                                            )
                                        }
                                    }
                                }

                                is StreamImageLine -> {
                                    val defaultHeight = streamImageRowHeight
                                    Box(
                                        Modifier
                                            .height(defaultHeight)
                                            .fillMaxWidth()
                                            .zIndex(1f)
                                            // The row slot stays at defaultHeight even when a hovered
                                            // image overflows it, so this is what the list lays out.
                                            .recordRowHeight(line.serialNumber, measuredHeights),
                                    ) {
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
    canHide: Boolean,
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
        if (canHide) {
            addItem(key = CloseContextMenuItemKey, label = "Hide window") {
                onCloseClick()
                close()
            }
        }
    }

/**
 * The width of the serial-number range the displayed lines occupy: one past the distance from the
 * oldest shown line to the newest. Serial numbers are handed out in order, so however many lines a
 * filter hides, every shown line falls inside this range.
 */
internal fun List<StreamLine>.serialSpan(): Long = if (isEmpty()) 0L else last().serialNumber - first().serialNumber + 1

/**
 * A modulus that folds ever-increasing serial numbers into a bounded set of lazy-list item keys.
 *
 * Keys must be unique among the rows present at once, and nothing more: a key that repeats after a
 * line is gone just reuses its cache entry, which is the point. Rows occupy a contiguous serial
 * range [serialSpan] wide, so any modulus greater than that span maps them to distinct keys. This
 * returns at least twice the span (and never less than [MIN_LAZY_ITEM_KEY_MODULUS]), rounded up to
 * a power of two so it changes rarely - a change re-keys every row, costing one full recomposition
 * of the list, so with the default scrollback it settles during warmup and then holds.
 */
internal fun lazyItemKeyModulus(serialSpan: Long): Long {
    var modulus = MIN_LAZY_ITEM_KEY_MODULUS
    // The rows on screen come out of the scrollback buffer, which removeLines caps, so the span has
    // a ceiling of about a million and this doubles nine times at the very most. Doubling rather
    // than computing a bit length keeps the growth obvious; halving the modulus rather than doubling
    // the span keeps the comparison away from any overflow question.
    while (modulus / 2 <= serialSpan) {
        modulus *= 2
    }
    return modulus
}

// Comfortably above the default scrollback, so the common case never re-keys after warmup.
private const val MIN_LAZY_ITEM_KEY_MODULUS = 4096L
