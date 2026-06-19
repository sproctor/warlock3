package warlockfe.warlock3.compose.desktop.ui.window

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.zIndex
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.theme.menuStyle
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.ui.game.WarlockGameChrome
import warlockfe.warlock3.compose.desktop.ui.settings.DesktopWindowSettingsDialog
import warlockfe.warlock3.compose.ui.window.ComposeTextStream
import warlockfe.warlock3.compose.ui.window.DialogWindowData
import warlockfe.warlock3.compose.ui.window.StreamImageLine
import warlockfe.warlock3.compose.ui.window.StreamLine
import warlockfe.warlock3.compose.ui.window.StreamTextLine
import warlockfe.warlock3.compose.ui.window.StreamWindowData
import warlockfe.warlock3.compose.ui.window.WindowBackgroundImage
import warlockfe.warlock3.compose.ui.window.WindowHeader
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.compose.ui.window.backgroundAlignment
import warlockfe.warlock3.compose.ui.window.isPreviousPrompt
import warlockfe.warlock3.compose.ui.window.isShowing
import warlockfe.warlock3.compose.util.ClearContextMenuItemKey
import warlockfe.warlock3.compose.util.CloseContextMenuItemKey
import warlockfe.warlock3.compose.util.SettingsContextMenuItemKey
import warlockfe.warlock3.compose.util.addItem
import warlockfe.warlock3.compose.util.createFontFamily
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.client.WarlockMenuItem
import warlockfe.warlock3.core.macro.ScrollEvent
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.BackgroundImageHorizontalAlignment
import warlockfe.warlock3.core.window.BackgroundImageMode
import warlockfe.warlock3.core.window.BackgroundImageVerticalAlignment
import warlockfe.warlock3.core.window.ClientBackgroundImage
import warlockfe.warlock3.core.window.WindowLocation

private val titleSmallStyle =
    TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
    )

@Composable
fun DesktopWindowView(
    uiState: WindowUiState,
    location: WindowLocation,
    defaultStyle: StyleDefinition,
    isSelected: Boolean,
    openWindows: List<String>,
    menuData: WarlockMenuData?,
    onActionClick: (WarlockAction) -> Int?,
    onCloseClick: () -> Unit,
    saveStyle: (StyleDefinition) -> Unit,
    saveNameFilter: (Boolean) -> Unit,
    onSelect: () -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    clearStream: () -> Unit,
) {
    val window by uiState.windowInfo
    var showWindowSettingsDialog by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()

    val title = (window?.title ?: uiState.name) + (window?.subtitle ?: "")
    var viewportHeight by remember { mutableIntStateOf(0) }
    val frameShape = RoundedCornerShape(4.dp)
    Box(
        modifier =
            modifier
                .padding(2.dp)
                .background(WarlockGameChrome.panel, frameShape)
                .border(
                    Dp.Hairline,
                    if (isSelected) WarlockGameChrome.borderStrong else WarlockGameChrome.border,
                    frameShape,
                ).onLayoutRectChanged { bounds ->
                    viewportHeight = bounds.height
                }.onFocusChanged { focusState ->
                    if (focusState.hasFocus) {
                        onSelect()
                    }
                }.semantics {
                    paneTitle = title
                },
    ) {
        Column {
            WindowHeader(
                modifier =
                    headerModifier
                        .background(
                            color = if (isSelected) WarlockGameChrome.accent else WarlockGameChrome.header,
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                        ).fillMaxWidth(),
                title = {
                    Text(
                        text = title,
                        color =
                            if (isSelected) {
                                WarlockGameChrome.accentText
                            } else {
                                WarlockGameChrome.textMuted
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = titleSmallStyle,
                    )
                },
                location = location,
                isSelected = isSelected,
                onSettingsClick = { showWindowSettingsDialog = true },
                onClearClick = clearStream,
                onCloseClick = onCloseClick,
            )

            when (val data = uiState.data) {
                is StreamWindowData -> {
                    DesktopWindowViewContent(
                        modifier =
                            Modifier.addTextContextMenuOptions(
                                windowLocation = location,
                                showSettingsDialog = { showWindowSettingsDialog = true },
                                onClearClick = clearStream,
                                onCloseClick = onCloseClick,
                            ),
                        stream = data.stream,
                        scrollState = scrollState,
                        style = uiState.style.mergeWith(defaultStyle),
                        backgroundImage = window?.backgroundImage,
                        openWindows = openWindows,
                        menuData = menuData,
                        onActionClick = onActionClick,
                    )
                }

                is DialogWindowData -> {
                    WarlockScrollableColumn(
                        Modifier
                            .fillMaxSize()
                            .background(
                                uiState.style
                                    .mergeWith(defaultStyle)
                                    .backgroundColor
                                    .toColor(),
                            ),
                    ) {
                        val dataObjects by data.dialogData.objects.collectAsState()
                        DesktopDialogContent(
                            dataObjects = dataObjects,
                            modifier = Modifier.padding(8.dp),
                            executeCommand = { command ->
                                onActionClick(WarlockAction.SendCommand(command))
                            },
                            style = uiState.style.mergeWith(defaultStyle),
                        )
                    }
                }

                else -> {
                    // Loading
                }
            }
        }
    }

    if (showWindowSettingsDialog) {
        DesktopWindowSettingsDialog(
            onCloseRequest = { showWindowSettingsDialog = false },
            style = uiState.style,
            defaultStyle = defaultStyle,
            saveStyle = saveStyle,
            nameFilterOption = window?.nameFilterOption ?: false,
            nameFilter = uiState.nameFilter,
            saveNameFilter = saveNameFilter,
        )
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
private fun Modifier.addTextContextMenuOptions(
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

@Composable
private fun DesktopWindowViewContent(
    stream: ComposeTextStream,
    scrollState: LazyListState,
    style: StyleDefinition,
    backgroundImage: ClientBackgroundImage?,
    openWindows: List<String>,
    menuData: WarlockMenuData?,
    onActionClick: (WarlockAction) -> Int?,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = style.backgroundColor.toColor()
    val textColor = style.textColor.toColor()
    val fontFamily = style.fontFamily?.let { createFontFamily(it) }
    val fontSize = style.fontSize?.sp ?: JewelTheme.defaultTextStyle.fontSize
    val fontWeight = style.fontWeight?.let { FontWeight(it) }

    val lines = stream.lines.collectAsState(emptyList()).value

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

    SelectionContainer(modifier = modifier) {
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
            VerticallyScrollableContainer(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
            ) {
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
                                            text = line.text,
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
        if (scrollState.lastScrolledBackward && scrollState.canScrollForward) {
            sticky = false
        } else if (!scrollState.canScrollForward) {
            sticky = true
        }
    }
}

private class OffsetPositionProvider(
    private val anchorPx: IntOffset,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x =
            (anchorBounds.left + anchorPx.x)
                .coerceAtMost(windowSize.width - popupContentSize.width)
                .coerceAtLeast(0)
        val y =
            (anchorBounds.top + anchorPx.y)
                .coerceAtMost(windowSize.height - popupContentSize.height)
                .coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

private sealed interface MenuNode {
    val label: String
}

private class MenuActionNode(
    override val label: String,
    val action: suspend () -> Unit,
) : MenuNode

private class MenuCategoryNode(
    override val label: String,
    val children: List<MenuNode>,
) : MenuNode

/**
 * Builds the drill-down menu tree from the flat item list, preserving the legacy category encoding:
 * the top-level grouping key is `category` up to the first `-`; a key containing `_` denotes a
 * submenu whose label is the part after the `_`. Within a submenu, items are grouped by the second
 * `-` segment, which (when present) is itself a verbatim sub-submenu label.
 */
private fun buildMenuTree(items: List<WarlockMenuItem>): List<MenuNode> {
    val groups = items.groupBy { it.category.split('-').first() }
    return groups.keys.sorted().flatMap { category ->
        val groupItems = groups.getValue(category)
        if (!category.contains('_')) {
            groupItems.map { MenuActionNode(it.label, it.action) }
        } else {
            val subgroups = groupItems.groupBy { it.category.split('-').getOrNull(1) }
            val children =
                buildList {
                    subgroups[null]?.forEach { add(MenuActionNode(it.label, it.action)) }
                    subgroups.keys.filterNotNull().sorted().forEach { subcategory ->
                        add(
                            MenuCategoryNode(
                                label = subcategory,
                                children = subgroups.getValue(subcategory).map { MenuActionNode(it.label, it.action) },
                            ),
                        )
                    }
                }
            listOf(MenuCategoryNode(label = category.split('_').getOrNull(1) ?: "Unknown", children = children))
        }
    }
}

@Composable
private fun ActionContextMenu(
    offset: Offset?,
    menuData: WarlockMenuData,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val anchorPx =
        offset?.let { IntOffset(it.x.toInt(), it.y.toInt()) } ?: IntOffset.Zero
    val positionProvider = remember(anchorPx) { OffsetPositionProvider(anchorPx) }
    // Items arrive asynchronously after the menu opens (initially empty), so key on the items, not
    // the menu id, or the tree would stay empty.
    val rootNodes = remember(menuData.items) { buildMenuTree(menuData.items) }
    // Nested popups grab focus on desktop and trap interaction, so we navigate within a single
    // popup instead: each category drills in, the back row returns to the previous level. Reset the
    // drill path only when a new menu opens (id changes), not when items load for the current one.
    var path by remember(menuData.id) { mutableStateOf<List<MenuCategoryNode>>(emptyList()) }
    PopupMenu(
        onDismissRequest = {
            onDismiss()
            true
        },
        popupPositionProvider = positionProvider,
    ) {
        val currentNodes = path.lastOrNull()?.children ?: rootNodes
        // Navigation rows use passiveItem + a custom clickable: Jewel's selectableItem force-closes
        // the whole menu on click, which would dismiss the popup instead of drilling in/out.
        if (path.isNotEmpty()) {
            passiveItem {
                NavMenuItem(
                    label = path.last().label,
                    leading = "\u2190",
                    onClick = { path = path.dropLast(1) },
                )
            }
            separator()
        }
        currentNodes.forEach { node ->
            when (node) {
                is MenuActionNode -> {
                    selectableItem(
                        selected = false,
                        onClick = {
                            scope.launch {
                                node.action()
                                onDismiss()
                            }
                        },
                    ) {
                        Text(node.label)
                    }
                }

                is MenuCategoryNode -> {
                    passiveItem {
                        NavMenuItem(
                            label = node.label,
                            trailing = "\u203A",
                            onClick = { path = path + node },
                        )
                    }
                }
            }
        }
    }
}

/** A menu row that navigates within the popup (drill in/out) without closing it. */
@Composable
private fun NavMenuItem(
    label: String,
    onClick: () -> Unit,
    leading: String? = null,
    trailing: String? = null,
) {
    val style = JewelTheme.menuStyle
    val itemColors = style.colors.itemColors
    val itemMetrics = style.metrics.itemMetrics
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    // Jewel's selectableItem highlights while focused and grabs focus on hover; take focus on hover
    // here too so the previously hovered selectable item doesn't stay highlighted.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is HoverInteraction.Enter) {
                focusRequester.requestFocus()
            }
        }
    }
    val background =
        when {
            pressed -> itemColors.backgroundPressed
            hovered -> itemColors.backgroundHovered
            else -> itemColors.background
        }
    val contentColor =
        when {
            pressed -> itemColors.contentPressed
            hovered -> itemColors.contentHovered
            else -> itemColors.content
        }
    androidx.compose.foundation.layout.Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .background(background, RoundedCornerShape(itemMetrics.selectionCornerSize))
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .defaultMinSize(minHeight = itemMetrics.minHeight)
                .padding(itemMetrics.contentPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(text = leading, color = contentColor)
        }
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            color = contentColor,
        )
        if (trailing != null) {
            Text(text = trailing, color = contentColor)
        }
    }
}
