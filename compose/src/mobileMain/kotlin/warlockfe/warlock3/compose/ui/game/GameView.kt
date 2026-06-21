package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.CompassButtonColors
import warlockfe.warlock3.compose.components.CompassButtons
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.settings_filled
import warlockfe.warlock3.compose.generated.resources.space_dashboard
import warlockfe.warlock3.compose.generated.resources.space_dashboard_filled
import warlockfe.warlock3.compose.ui.window.DialogContent
import warlockfe.warlock3.compose.ui.window.DragDropState
import warlockfe.warlock3.compose.ui.window.DragOverlay
import warlockfe.warlock3.compose.ui.window.DropResult
import warlockfe.warlock3.compose.ui.window.LocalProgressBarColors
import warlockfe.warlock3.compose.ui.window.ProgressBarColorState
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.compose.ui.window.WindowView
import warlockfe.warlock3.compose.ui.window.WindowsAtLocation
import warlockfe.warlock3.compose.util.MobileGameLayout
import warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE
import warlockfe.warlock3.compose.util.WindowWidthSizeClass
import warlockfe.warlock3.compose.util.gameLayout
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.macro.ScrollEvent
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.WindowLocation

/**
 * The mobile game screen. Responsive across Material 3 width size classes: a [PhoneGameView] (all
 * windows in tabs) at Compact/Medium widths, a [TabletGameView] (main + a tabbed pane) at Expanded,
 * and the drag-and-drop [LargeGameView] at Large/Extra-large. The disconnected banner, macro-error
 * dialog and key-to-focus routing are shared across all three.
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun GameView(
    viewModel: GameViewModel,
    navigateToDashboard: () -> Unit,
    openSettings: () -> Unit,
    openDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entryFocusRequester = remember { FocusRequester() }
    // On JVM, KeyDown is followed by an Unknown/KEY_TYPED event
    var ignoreNextUnknownKeyEvent by remember { mutableStateOf(false) }
    Surface(
        modifier =
            modifier.onPreviewKeyEvent { event ->
                if (ignoreNextUnknownKeyEvent && event.type == KeyEventType.Unknown) {
                    ignoreNextUnknownKeyEvent = false
                    return@onPreviewKeyEvent true
                }
                ignoreNextUnknownKeyEvent = false
                viewModel.handleKeyPress(event).also {
                    ignoreNextUnknownKeyEvent = it
                    if (!it &&
                        event.type == KeyEventType.KeyDown &&
                        !event.isAltPressed &&
                        !event.isCtrlPressed &&
                        !event.isMetaPressed &&
                        !event.isShiftPressed
                    ) {
                        entryFocusRequester.requestFocus()
                    }
                }
            },
    ) {
        BoxWithConstraints {
            val layout = WindowWidthSizeClass.fromWidth(maxWidth).gameLayout()
            Column(Modifier.fillMaxSize()) {
                val progressBarSettings by viewModel.progressBarSettings.collectAsState()
                CompositionLocalProvider(
                    LocalProgressBarColors provides
                        ProgressBarColorState(
                            settings = progressBarSettings,
                            saveColors = viewModel::saveProgressBarColors,
                        ),
                ) {
                    when (layout) {
                        MobileGameLayout.Phone -> {
                            PhoneGameView(
                                viewModel = viewModel,
                                entryFocusRequester = entryFocusRequester,
                                navigateToDashboard = navigateToDashboard,
                                openSettings = openSettings,
                                openDrawer = openDrawer,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        MobileGameLayout.Tablet -> {
                            TabletGameView(
                                viewModel = viewModel,
                                entryFocusRequester = entryFocusRequester,
                                openSettings = openSettings,
                                navigateToDashboard = navigateToDashboard,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        MobileGameLayout.Large -> {
                            LargeGameView(
                                viewModel = viewModel,
                                entryFocusRequester = entryFocusRequester,
                                openSettings = openSettings,
                                navigateToDashboard = navigateToDashboard,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        val macroError by viewModel.macroError.collectAsState()
        if (macroError != null) {
            AlertDialog(
                onDismissRequest = { viewModel.handledMacroError() },
                confirmButton = {
                    TextButton(onClick = { viewModel.handledMacroError() }) {
                        Text("Ok")
                    }
                },
                text = { Text(macroError!!) },
            )
        }
    }
}

@Composable
fun GameTextWindows(
    topWindowUiStates: List<WindowUiState>,
    bottomWindowUiStates: List<WindowUiState>,
    leftWindowUiStates: List<WindowUiState>,
    rightWindowUiStates: List<WindowUiState>,
    mainWindowUiState: WindowUiState?,
    defaultStyle: StyleDefinition,
    selectedWindow: String,
    openWindows: List<String>,
    topHeight: Int?,
    bottomHeight: Int?,
    leftWidth: Int?,
    rightWidth: Int?,
    menuData: WarlockMenuData?,
    onActionClick: (WarlockAction) -> Int?,
    onHeightChange: (String, Int) -> Unit,
    onWidthChange: (String, Int) -> Unit,
    onSizeChange: (WindowLocation, Int) -> Unit,
    onDrop: (DropResult) -> Unit,
    onCloseClick: (String) -> Unit,
    saveStyle: (String, StyleDefinition) -> Unit,
    saveNameFilter: (String, Boolean) -> Unit,
    onWindowSelect: (String) -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    modifier: Modifier = Modifier,
    clearStream: (String) -> Unit,
) {
    val dragDropState = remember { DragDropState() }

    Box(modifier = modifier) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left column
            WindowsAtLocation(
                location = WindowLocation.LEFT,
                size = leftWidth,
                windowUiStates = leftWindowUiStates,
                defaultStyle = defaultStyle,
                openWindows = openWindows,
                horizontalPanel = true,
                handleBefore = false,
                selectedWindow = selectedWindow,
                onSizeChange = { onSizeChange(WindowLocation.LEFT, it) },
                menuData = menuData,
                onActionClick = onActionClick,
                onHeightChange = onHeightChange,
                onWidthChange = onWidthChange,
                onCloseClick = onCloseClick,
                saveStyle = saveStyle,
                saveNameFilter = saveNameFilter,
                onWindowSelect = onWindowSelect,
                scrollEvents = scrollEvents,
                handledScrollEvent = handledScrollEvent,
                clearStream = clearStream,
                dragDropState = dragDropState,
                onDrop = onDrop,
            )
            // Center column
            Column(modifier = Modifier.weight(1f)) {
                WindowsAtLocation(
                    location = WindowLocation.TOP,
                    size = topHeight,
                    windowUiStates = topWindowUiStates,
                    defaultStyle = defaultStyle,
                    openWindows = openWindows,
                    horizontalPanel = false,
                    handleBefore = false,
                    selectedWindow = selectedWindow,
                    onSizeChange = { onSizeChange(WindowLocation.TOP, it) },
                    menuData = menuData,
                    onActionClick = onActionClick,
                    onHeightChange = onHeightChange,
                    onWidthChange = onWidthChange,
                    onCloseClick = onCloseClick,
                    saveStyle = saveStyle,
                    saveNameFilter = saveNameFilter,
                    onWindowSelect = onWindowSelect,
                    scrollEvents = scrollEvents,
                    handledScrollEvent = handledScrollEvent,
                    clearStream = clearStream,
                    dragDropState = dragDropState,
                    onDrop = onDrop,
                )
                if (mainWindowUiState != null) {
                    WindowView(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        headerModifier = Modifier,
                        uiState = mainWindowUiState,
                        location = WindowLocation.MAIN,
                        defaultStyle = defaultStyle,
                        isSelected = selectedWindow == mainWindowUiState.name,
                        openWindows = openWindows,
                        menuData = menuData,
                        onActionClick = onActionClick,
                        onCloseClick = {},
                        saveStyle = {
                            saveStyle(mainWindowUiState.name, it)
                        },
                        saveNameFilter = {
                            saveNameFilter(mainWindowUiState.name, it)
                        },
                        onSelect = { onWindowSelect(mainWindowUiState.name) },
                        scrollEvents = scrollEvents,
                        handledScrollEvent = handledScrollEvent,
                        clearStream = { clearStream(mainWindowUiState.name) },
                    )
                }
                WindowsAtLocation(
                    location = WindowLocation.BOTTOM,
                    size = bottomHeight,
                    windowUiStates = bottomWindowUiStates,
                    defaultStyle = defaultStyle,
                    openWindows = openWindows,
                    horizontalPanel = false,
                    handleBefore = true,
                    selectedWindow = selectedWindow,
                    onSizeChange = { onSizeChange(WindowLocation.BOTTOM, it) },
                    menuData = menuData,
                    onActionClick = onActionClick,
                    onHeightChange = onHeightChange,
                    onWidthChange = onWidthChange,
                    onCloseClick = onCloseClick,
                    saveStyle = saveStyle,
                    saveNameFilter = saveNameFilter,
                    onWindowSelect = onWindowSelect,
                    scrollEvents = scrollEvents,
                    handledScrollEvent = handledScrollEvent,
                    clearStream = clearStream,
                    dragDropState = dragDropState,
                    onDrop = onDrop,
                )
            }
            // Right Column
            WindowsAtLocation(
                location = WindowLocation.RIGHT,
                size = rightWidth,
                windowUiStates = rightWindowUiStates,
                defaultStyle = defaultStyle,
                openWindows = openWindows,
                horizontalPanel = true,
                handleBefore = true,
                selectedWindow = selectedWindow,
                onSizeChange = { onSizeChange(WindowLocation.RIGHT, it) },
                menuData = menuData,
                onActionClick = onActionClick,
                onHeightChange = onHeightChange,
                onWidthChange = onWidthChange,
                onCloseClick = onCloseClick,
                saveStyle = saveStyle,
                saveNameFilter = saveNameFilter,
                onWindowSelect = onWindowSelect,
                scrollEvents = scrollEvents,
                handledScrollEvent = handledScrollEvent,
                clearStream = clearStream,
                dragDropState = dragDropState,
                onDrop = onDrop,
            )
        }
        // Drag overlay rendered on top of everything
        DragOverlay(dragDropState = dragDropState)
    }
}

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun GameBottomBar(
    viewModel: GameViewModel,
    entryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    openSettings: (() -> Unit)? = null,
    windowListVisible: Boolean = false,
    onToggleWindowList: (() -> Unit)? = null,
    disconnected: Boolean = false,
    canReconnect: Boolean = false,
    onReconnect: (() -> Unit)? = null,
    onDashboard: (() -> Unit)? = null,
) {
    val presets by viewModel.presets.collectAsState(emptyMap())
    val style = presets["default"] ?: SAFE_DEFAULT_STYLE
    val backgroundColor = style.backgroundColor.toColor()
    val textColor = style.textColor.toColor()
    BoxWithConstraints(modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Disconnect actions for layouts without a top bar (tablet): the old banner is gone.
                if (disconnected && (onDashboard != null || (canReconnect && onReconnect != null))) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canReconnect && onReconnect != null) {
                            FilledTonalButton(onClick = onReconnect) { Text("Reconnect") }
                        }
                        if (onDashboard != null) {
                            FilledTonalButton(onClick = onDashboard) { Text("Dashboard") }
                        }
                    }
                }
                val actionBar by viewModel.actionBar.collectAsState()
                if (actionBar.toolbar.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        actionBar.toolbar.forEach { action ->
                            ActionChip(
                                action = action,
                                pool = actionBar.actions,
                                onRunLeaf = viewModel::runActionScript,
                            )
                        }
                    }
                }
                WarlockEntry(
                    viewModel = viewModel,
                    entryFocusRequester = entryFocusRequester,
                )
                if (onToggleWindowList != null || openSettings != null) {
                    Row {
                        if (onToggleWindowList != null) {
                            IconButton(onClick = onToggleWindowList) {
                                Icon(
                                    painter =
                                        painterResource(
                                            if (windowListVisible) {
                                                Res.drawable.space_dashboard_filled
                                            } else {
                                                Res.drawable.space_dashboard
                                            },
                                        ),
                                    contentDescription = "Toggle window list",
                                )
                            }
                        }
                        if (openSettings != null) {
                            IconButton(onClick = openSettings) {
                                Icon(
                                    painter = painterResource(Res.drawable.settings_filled),
                                    contentDescription = "Settings",
                                )
                            }
                        }
                    }
                }
                val vitalBars by viewModel.vitalBars.objects.collectAsState()
                DialogContent(
                    dataObjects = vitalBars,
                    modifier = Modifier.fillMaxWidth().height(16.dp),
                    executeCommand = {
                        // Cannot execute commands from vitals bar
                    },
                    style = style,
                )
                HandsView(
                    left = viewModel.leftHand.collectAsState(null).value,
                    right = viewModel.rightHand.collectAsState(null).value,
                    spell = viewModel.spellHand.collectAsState(null).value,
                )
            }
            val indicators by viewModel.indicators.collectAsState(emptySet())
            IndicatorView(
                indicatorSize = (this@BoxWithConstraints.maxWidth / 20).coerceIn(24.dp, 60.dp),
                backgroundColor = backgroundColor,
                defaultColor = textColor,
                indicators = indicators,
            )
            CompassButtons(
                height = 88.dp,
                directions = viewModel.compassState.collectAsState().value,
                onClick = { direction ->
                    viewModel.sendCommand(direction.value)
                },
                colors =
                    CompassButtonColors(
                        litBackground = MaterialTheme.colorScheme.secondaryContainer,
                        litBorder = MaterialTheme.colorScheme.primary,
                        litIcon = MaterialTheme.colorScheme.onSecondaryContainer,
                        background = MaterialTheme.colorScheme.surfaceVariant,
                        border = MaterialTheme.colorScheme.outlineVariant,
                        icon = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    ),
            )
        }
    }
}

/** Shared handling for a clickable game-text action (command link or menu). */
internal fun GameViewModel.onWindowAction(action: WarlockAction): Int? =
    when (action) {
        is WarlockAction.SendCommand -> {
            sendCommand(action.command)
            null
        }

        is WarlockAction.SendCommandWithLookup -> {
            sendCommand(action.command)
            null
        }

        is WarlockAction.OpenMenu -> {
            action.onClick()
        }

        else -> {
            null
        }
    }

/** Shared handling for a window drag-and-drop result (reorder within, or move across, locations). */
internal fun GameViewModel.onWindowDrop(result: DropResult) {
    if (result.sourceLocation == result.target.location) {
        changeWindowPositions(
            result.sourceLocation,
            result.sourceIndex,
            result.target.insertionIndex,
        )
    } else {
        moveWindowToPosition(
            result.name,
            result.target.location,
            result.target.insertionIndex,
        )
    }
}
