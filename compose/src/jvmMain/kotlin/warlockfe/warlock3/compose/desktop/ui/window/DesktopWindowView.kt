package warlockfe.warlock3.compose.desktop.ui.window

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.theme.menuStyle
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.ui.game.gameChrome
import warlockfe.warlock3.compose.desktop.ui.settings.DesktopWindowSettingsDialog
import warlockfe.warlock3.compose.ui.window.WindowHeader
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.compose.ui.window.WindowViewScaffold
import warlockfe.warlock3.compose.ui.window.scrollbarSkinColors
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.client.WarlockMenuItem
import warlockfe.warlock3.core.macro.ScrollEvent
import warlockfe.warlock3.core.text.StyleDefinition
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
    WindowViewScaffold(
        uiState = uiState,
        location = location,
        defaultStyle = defaultStyle,
        isSelected = isSelected,
        openWindows = openWindows,
        menuData = menuData,
        onActionClick = onActionClick,
        onCloseClick = onCloseClick,
        onSelect = onSelect,
        clearStream = clearStream,
        scrollEvents = scrollEvents,
        handledScrollEvent = handledScrollEvent,
        modifier = modifier,
        defaultFontSize = JewelTheme.defaultTextStyle.fontSize,
        surface = { surfaceModifier, content ->
            val frameShape = RoundedCornerShape(4.dp)
            Box(
                modifier =
                    surfaceModifier
                        // Clip so a body that paints its own background (dialog panels, user styles) does not
                        // overdraw the rounded corners.
                        .clip(frameShape)
                        .background(gameChrome.panel, frameShape)
                        .border(
                            Dp.Hairline,
                            if (isSelected) gameChrome.borderStrong else gameChrome.border,
                            frameShape,
                        ),
            ) {
                content()
            }
        },
        header = { title, onSettingsClick ->
            WindowHeader(
                modifier =
                    headerModifier
                        .background(
                            color = if (isSelected) gameChrome.accent else gameChrome.header,
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                        ).fillMaxWidth(),
                title = {
                    Text(
                        text = title,
                        color =
                            if (isSelected) {
                                gameChrome.accentText
                            } else {
                                gameChrome.textMuted
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = titleSmallStyle,
                    )
                },
                location = location,
                isSelected = isSelected,
                onSettingsClick = onSettingsClick,
                onClearClick = clearStream,
                onCloseClick = onCloseClick,
            )
        },
        listContainer = { _, heightModel, content ->
            // The stock lazy-list scrollbar (Jewel's VerticallyScrollableContainer) extrapolates the
            // content size from the average height of the visible lines, so its thumb jumps when
            // lines wrap to different heights. Drive a native scrollbar from a custom adapter backed
            // by per-line measured heights instead; the LazyColumn still handles wheel scrolling.
            Box(Modifier.fillMaxSize()) {
                content()
                val adapter = remember(heightModel) { MeasuredScrollbarAdapter(heightModel) }
                val scrollbar = scrollbarSkinColors
                // Share an interaction source with the scrollbar so the track gutter follows the
                // thumb's own state: VerticalScrollbar drives its highlight from this same source
                // (hoverable + DragInteraction), so the gutter fades in (to its skin alpha) exactly
                // when the thumb does and stays hidden while idle. canScroll keeps the lane clear when
                // content fits.
                val interactionSource = remember { MutableInteractionSource() }
                val hovered by interactionSource.collectIsHoveredAsState()
                val dragged by interactionSource.collectIsDraggedAsState()
                val canScroll = heightModel.contentSize > heightModel.viewportSize
                val gutterAlpha by animateFloatAsState(
                    targetValue = if (canScroll && (hovered || dragged)) 0.1f else 0f,
                    label = "scrollbarGutter",
                )
                VerticalScrollbar(
                    adapter = adapter,
                    interactionSource = interactionSource,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .background(scrollbar.gutter.copy(alpha = gutterAlpha))
                            .padding(top = 2.dp, bottom = 4.dp),
                    style =
                        defaultScrollbarStyle().copy(
                            minimalHeight = 24.dp,
                            unhoverColor = scrollbar.thumb,
                            hoverColor = scrollbar.thumb,
                        ),
                )
            }
        },
        actionContextMenu = { offset, menu, onDismiss ->
            ActionContextMenu(offset = offset, menuData = menu, onDismiss = onDismiss)
        },
        settingsDialog = { onCloseRequest ->
            DesktopWindowSettingsDialog(
                onCloseRequest = onCloseRequest,
                style = uiState.style,
                defaultStyle = defaultStyle,
                saveStyle = saveStyle,
                nameFilterOption = window?.nameFilterOption ?: false,
                nameFilter = uiState.nameFilter,
                saveNameFilter = saveNameFilter,
            )
        },
        dialogContent = { data, style ->
            WarlockScrollableColumn(
                Modifier
                    .fillMaxSize()
                    .background(style.backgroundColor.toColor()),
            ) {
                val dataObjects by data.dialogData.objects.collectAsState()
                DesktopDialogContent(
                    dataObjects = dataObjects,
                    modifier = Modifier.padding(8.dp),
                    executeCommand = { command ->
                        onActionClick(WarlockAction.SendCommand(command))
                    },
                    style = style,
                )
            }
        },
    )
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
