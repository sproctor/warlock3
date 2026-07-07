package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import io.github.oikvpqya.compose.fastscroller.ThumbStyle
import io.github.oikvpqya.compose.fastscroller.TrackStyle
import io.github.oikvpqya.compose.fastscroller.VerticalScrollbar
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.components.defaultScrollbarStyle
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.arrow_right
import warlockfe.warlock3.compose.ui.settings.WindowSettingsDialog
import warlockfe.warlock3.compose.util.LocalWindowFontSaver
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.macro.ScrollEvent
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.WindowLocation

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WindowView(
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
        defaultFontSize = MaterialTheme.typography.bodyMedium.fontSize,
        surface = { surfaceModifier, content ->
            Surface(
                surfaceModifier,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outline),
            ) {
                content()
            }
        },
        header = { title, onSettingsClick ->
            WindowHeader(
                modifier =
                    headerModifier
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        ).fillMaxWidth(),
                title = {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
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
            // Drive the fastscroller scrollbar from the measured-height scroll model (the same height
            // logic the desktop scrollbar uses) so the drag handle's length stays stable instead of
            // jumping when lines wrap to different heights, which is what the stock lazy-list adapter's
            // visible-average estimate causes.
            // TODO: migrate to Modifier.scrollIndicator once foundation ships ScrollIndicatorFactory +
            // the scrollIndicator modifier (absent in foundation 1.11.2; only the read-only
            // ScrollIndicatorState exists). It would need a custom ScrollIndicatorState wrapping this
            // model (Double -> Int) and is draw-only, so keep fastscroller for the drag-to-scroll thumb.
            Box(Modifier.fillMaxSize()) {
                content()
                val adapter = remember(heightModel) { MeasuredScrollbarAdapter(heightModel) }
                val scrollbar = scrollbarSkinColors
                VerticalScrollbar(
                    adapter = adapter,
                    // Same skin colors the desktop scrollbar uses (gutter track + thumb).
                    style =
                        defaultScrollbarStyle(
                            thumbStyle =
                                ThumbStyle(
                                    shape = RoundedCornerShape(4.dp),
                                    unhoverColor = scrollbar.thumb,
                                    hoverColor = scrollbar.thumb,
                                ),
                            trackStyle =
                                TrackStyle(
                                    shape = RoundedCornerShape(4.dp),
                                    unhoverColor = scrollbar.gutter,
                                    hoverColor = scrollbar.gutter,
                                ),
                        ),
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                )
            }
        },
        actionContextMenu = { offset, menu, onDismiss ->
            ActionContextMenu(offset = offset, menuData = menu, onDismiss = onDismiss)
        },
        settingsDialog = { onCloseRequest ->
            val fontSaver = LocalWindowFontSaver.current
            WindowSettingsDialog(
                onCloseRequest = onCloseRequest,
                style = uiState.style,
                defaultStyle = defaultStyle,
                saveStyle = saveStyle,
                font = uiState.font,
                monoFont = uiState.monoFont,
                saveFont = { fontSaver.saveFont(uiState.name, it) },
                saveMonoFont = { fontSaver.saveMonoFont(uiState.name, it) },
                nameFilterOption = window?.nameFilterOption ?: false,
                nameFilter = uiState.nameFilter,
                saveNameFilter = saveNameFilter,
            )
        },
        dialogContent = { data, style ->
            ScrollableColumn(
                Modifier
                    .fillMaxSize()
                    .background(style.backgroundColor.toColor()),
            ) {
                val dataObjects by data.dialogData.objects.collectAsState()
                DialogContent(
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

@Composable
private fun ActionContextMenu(
    offset: Offset?,
    menuData: WarlockMenuData,
    onDismiss: () -> Unit,
) {
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
                    Logger.d { "Menu item: $item" }
                    DropdownMenuItem(
                        text = {
                            Text(item.label)
                        },
                        onClick = {
                            scope.launch {
                                item.action()
                                onDismiss()
                            }
                        },
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
                    },
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
                            },
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
                                    contentDescription = "expandable",
                                )
                            },
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
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
