package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.CompassView
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.visibility_filled
import warlockfe.warlock3.compose.generated.resources.visibility_off_filled
import warlockfe.warlock3.compose.ui.window.DialogContent
import warlockfe.warlock3.compose.ui.window.ScrollEvent
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.compose.ui.window.WindowView
import warlockfe.warlock3.compose.ui.window.WindowsAtLocation
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.prefs.repositories.defaultStyles
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.Window
import warlockfe.warlock3.core.window.WindowLocation

@Composable
fun GameView(
    viewModel: GameViewModel,
    navigateToDashboard: () -> Unit,
    sideBarVisible: Boolean,
) {
    val disconnected by viewModel.disconnected.collectAsState()

    val entryFocusRequester = remember { FocusRequester() }
    // On JVM, KeyDown is followed by an Unknown/KEY_TYPED event
    var ignoreNextUnknownKeyEvent by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (ignoreNextUnknownKeyEvent && event.type == KeyEventType.Unknown) {
                ignoreNextUnknownKeyEvent = false
                return@onPreviewKeyEvent true
            }
            ignoreNextUnknownKeyEvent = false
            viewModel.handleKeyPress(event).also {
                // on JVM, the next event will update the TextEntry, ignore it if we handled this one
                // on Android, the next event will be a KeyUp and we don't care
                ignoreNextUnknownKeyEvent = it
                // Focus the entry on normal key presses
                if (!it && event.type == KeyEventType.KeyDown && !event.isAltPressed && !event.isCtrlPressed
                    && !event.isMetaPressed && !event.isShiftPressed
                ) {
                    entryFocusRequester.requestFocus()
                }
            }
        },
    ) {
        Column {
            if (disconnected) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(color = Color(red = 0xff, green = 0xcc, blue = 0))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("You have been disconnected from the server")
                    FilledTonalButton(onClick = navigateToDashboard) {
                        Text("Back to dashboard")
                    }
                }
            }

            val subWindows = viewModel.windowUiStates.collectAsState()
            val mainWindow = viewModel.mainWindowUiState.collectAsState()
            val menuData: WarlockMenuData? by viewModel.menuData.collectAsState()

            Row(modifier = Modifier.weight(1f)) {
                if (sideBarVisible) {
                    val windows by viewModel.windowRepository.windows.collectAsState()
                    val openWindows by viewModel.windowRepository.openWindows.collectAsState(emptyList())
                    val scope = rememberCoroutineScope()
                    val uiState by viewModel.mainWindowUiState.collectAsState()
                    ScrollableColumn(
                        Modifier
                            .padding(2.dp)
                            .fillMaxHeight()
                            .width(240.dp)
                            .background(
                                color = uiState?.defaultStyle?.backgroundColor?.toColor()
                                    ?: MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.extraSmall,
                            )
                            .border(
                                width = Dp.Hairline,
                                color = MaterialTheme.colorScheme.outline,
                                shape = MaterialTheme.shapes.extraSmall,
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        windows.values.sortedBy { it.title }.forEach { window ->
                            WindowListItem(
                                color = uiState?.defaultStyle?.textColor?.toColor()
                                    ?: MaterialTheme.colorScheme.onSurface,
                                window = window,
                                isOpen = openWindows.contains(window.name),
                                onClick = { open ->
                                    scope.launch {
                                        if (open) viewModel.windowRepository.openWindow(window.name)
                                        else viewModel.windowRepository.closeWindow(window.name)
                                    }
                                },
                            )
                        }
                    }
                }
                GameTextWindows(
                    modifier = Modifier.weight(1f),
                    subWindowUiStates = subWindows.value,
                    mainWindowUiState = mainWindow.value,
                    selectedWindow = viewModel.selectedWindow.collectAsState().value,
                    topHeight = viewModel.topHeight.collectAsState(null).value,
                    bottomHeight = viewModel.bottomHeight.collectAsState(null).value,
                    leftWidth = viewModel.leftWidth.collectAsState(null).value,
                    rightWidth = viewModel.rightWidth.collectAsState(null).value,
                    menuData = menuData,
                    onActionClicked = { action ->
                        when (action) {
                            is WarlockAction.SendCommand -> {
                                viewModel.sendCommand(action.command)
                                null
                            }

                            is WarlockAction.SendCommandWithLookup -> {
                                viewModel.sendCommand(action.command)
                                null
                            }

                            is WarlockAction.OpenMenu -> {
                                action.onClick()
                            }

                            else -> null
                        }
                    },
                    onMoveClicked = { name, location ->
                        viewModel.moveWindow(name = name, location = location)
                    },
                    onWidthChanged = { name, width ->
                        viewModel.setWindowWidth(name, width)
                    },
                    onHeightChanged = { name, height ->
                        viewModel.setWindowHeight(name, height)
                    },
                    onSizeChanged = viewModel::setLocationSize,
                    onMoveWindow = viewModel::changeWindowPositions,
                    onCloseClicked = viewModel::closeWindow,
                    saveStyle = viewModel::saveWindowStyle,
                    onWindowSelected = viewModel::selectWindow,
                    scrollEvents = viewModel.scrollEvents.collectAsState().value,
                    handledScrollEvent = viewModel::handledScrollEvent,
                    clearStream = viewModel::clearStream,
                )
            }
            GameBottomBar(viewModel, entryFocusRequester)
        }
        val macroError by viewModel.macroError.collectAsState()
        if (macroError != null) {
            AlertDialog(
                onDismissRequest = {
                    viewModel.handledMacroError()
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.handledMacroError()
                        }
                    ) {
                        Text("Ok")
                    }
                },
                text = {
                    Text(macroError!!)
                }
            )
        }
    }
}

@Composable
fun GameTextWindows(
    modifier: Modifier,
    subWindowUiStates: List<WindowUiState>,
    mainWindowUiState: WindowUiState?,
    selectedWindow: String,
    topHeight: Int?,
    bottomHeight: Int?,
    leftWidth: Int?,
    rightWidth: Int?,
    menuData: WarlockMenuData?,
    onActionClicked: (WarlockAction) -> Int?,
    onMoveClicked: (name: String, WindowLocation) -> Unit,
    onHeightChanged: (String, Int) -> Unit,
    onWidthChanged: (String, Int) -> Unit,
    onSizeChanged: (WindowLocation, Int) -> Unit,
    onMoveWindow: (WindowLocation, Int, Int) -> Unit,
    onCloseClicked: (String) -> Unit,
    saveStyle: (String, StyleDefinition) -> Unit,
    onWindowSelected: (String) -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    clearStream: (String) -> Unit,
) {
    // Container for all window views
    Row(modifier = modifier) {
        // Left column
        WindowsAtLocation(
            location = WindowLocation.LEFT,
            size = leftWidth,
            windowUiStates = subWindowUiStates,
            horizontalPanel = true,
            handleBefore = false,
            selectedWindow = selectedWindow,
            onSizeChanged = { onSizeChanged(WindowLocation.LEFT, it) },
            menuData = menuData,
            onActionClicked = onActionClicked,
            onMoveClicked = onMoveClicked,
            onHeightChanged = onHeightChanged,
            onWidthChanged = onWidthChanged,
            onMoveWindow = { from, to -> onMoveWindow(WindowLocation.LEFT, from, to) },
            onCloseClicked = onCloseClicked,
            saveStyle = saveStyle,
            onWindowSelected = onWindowSelected,
            scrollEvents = scrollEvents,
            handledScrollEvent = handledScrollEvent,
            clearStream = clearStream,
        )
        // Center column
        Column(modifier = Modifier.weight(1f)) {
            WindowsAtLocation(
                location = WindowLocation.TOP,
                size = topHeight,
                windowUiStates = subWindowUiStates,
                horizontalPanel = false,
                handleBefore = false,
                selectedWindow = selectedWindow,
                onSizeChanged = { onSizeChanged(WindowLocation.TOP, it) },
                menuData = menuData,
                onActionClicked = onActionClicked,
                onMoveClicked = onMoveClicked,
                onHeightChanged = onHeightChanged,
                onWidthChanged = onWidthChanged,
                onMoveWindow = { from, to -> onMoveWindow(WindowLocation.TOP, from, to) },
                onCloseClicked = onCloseClicked,
                saveStyle = saveStyle,
                onWindowSelected = onWindowSelected,
                scrollEvents = scrollEvents,
                handledScrollEvent = handledScrollEvent,
                clearStream = clearStream,
            )
            if (mainWindowUiState != null) {
                WindowView(
                    modifier = Modifier.fillMaxWidth().weight(1f), //.focusRequester(focusRequester),
                    headerModifier = Modifier,
                    uiState = mainWindowUiState,
                    isSelected = selectedWindow == mainWindowUiState.name,
                    menuData = menuData,
                    onActionClicked = onActionClicked,
                    onMoveClicked = {},
                    onCloseClicked = {},
                    saveStyle = {
                        saveStyle(mainWindowUiState.name, it)
                    },
                    onSelected = { onWindowSelected(mainWindowUiState.name) },
                    scrollEvents = scrollEvents,
                    handledScrollEvent = handledScrollEvent,
                    clearStream = { clearStream(mainWindowUiState.name) },
                )
            }
            WindowsAtLocation(
                location = WindowLocation.BOTTOM,
                size = bottomHeight,
                windowUiStates = subWindowUiStates,
                horizontalPanel = false,
                handleBefore = true,
                selectedWindow = selectedWindow,
                onSizeChanged = { onSizeChanged(WindowLocation.BOTTOM, it) },
                menuData = menuData,
                onActionClicked = onActionClicked,
                onMoveClicked = onMoveClicked,
                onHeightChanged = onHeightChanged,
                onWidthChanged = onWidthChanged,
                onMoveWindow = { from, to -> onMoveWindow(WindowLocation.BOTTOM, from, to) },
                onCloseClicked = onCloseClicked,
                saveStyle = saveStyle,
                onWindowSelected = onWindowSelected,
                scrollEvents = scrollEvents,
                handledScrollEvent = handledScrollEvent,
                clearStream = clearStream,
            )
        }
        // Right Column
        WindowsAtLocation(
            location = WindowLocation.RIGHT,
            size = rightWidth,
            windowUiStates = subWindowUiStates,
            horizontalPanel = true,
            handleBefore = true,
            selectedWindow = selectedWindow,
            onSizeChanged = { onSizeChanged(WindowLocation.RIGHT, it) },
            menuData = menuData,
            onActionClicked = onActionClicked,
            onMoveClicked = onMoveClicked,
            onHeightChanged = onHeightChanged,
            onWidthChanged = onWidthChanged,
            onMoveWindow = { from, to -> onMoveWindow(WindowLocation.RIGHT, from, to) },
            onCloseClicked = onCloseClicked,
            saveStyle = saveStyle,
            onWindowSelected = onWindowSelected,
            scrollEvents = scrollEvents,
            handledScrollEvent = handledScrollEvent,
            clearStream = clearStream,
        )
    }
}

@Composable
fun GameBottomBar(
    viewModel: GameViewModel,
    entryFocusRequester: FocusRequester,
) {
    val presets by viewModel.presets.collectAsState(emptyMap())
    val style = presets["default"] ?: defaultStyles["default"]!!
    val backgroundColor = style.backgroundColor.toColor()
    val textColor = style.textColor.toColor()
    BoxWithConstraints {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                WarlockEntry(
                    viewModel = viewModel,
                    entryFocusRequester = entryFocusRequester,
                )
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
            CompassView(
                size = 88.dp,
                state = viewModel.compassState.collectAsState().value,
                onClick = {
                    viewModel.sendCommand(it.abbreviation)
                }
            )
        }
    }
}

@Composable
private fun WindowListItem(color: Color, window: Window, isOpen: Boolean, onClick: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = { onClick(!isOpen) }),
    ) {
        Icon(
            painterResource(
                if (isOpen) {
                    Res.drawable.visibility_filled
                } else {
                    Res.drawable.visibility_off_filled
                }
            ),
            tint = color,
            contentDescription = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(text = window.title, color = color)
    }
}