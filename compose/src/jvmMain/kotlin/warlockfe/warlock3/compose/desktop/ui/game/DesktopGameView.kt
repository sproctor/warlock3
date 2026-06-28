package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockAlertDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.circle
import warlockfe.warlock3.compose.generated.resources.circle_filled
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.window.LocalProgressBarSettings
import warlockfe.warlock3.compose.ui.window.LocalWindowFindController
import warlockfe.warlock3.compose.ui.window.ProgressBarSettingsState
import warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.text.isSpecified
import warlockfe.warlock3.core.window.WindowInfo

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun DesktopGameView(
    viewModel: GameViewModel,
    sideBarVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val entryFocusRequester = remember { FocusRequester() }
    var ignoreNextUnknownKeyEvent by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(gameChrome.appBackground)
                .onPreviewKeyEvent { event ->
                    if (ignoreNextUnknownKeyEvent && event.type == KeyEventType.Unknown) {
                        ignoreNextUnknownKeyEvent = false
                        return@onPreviewKeyEvent true
                    }
                    ignoreNextUnknownKeyEvent = false
                    // The find bar handles its own keys while it's focused; step aside so it can.
                    if (viewModel.windowFindController.focused.value) {
                        return@onPreviewKeyEvent false
                    }
                    viewModel.handleKeyPress(event).also {
                        ignoreNextUnknownKeyEvent = it
                        if (!it &&
                            event.type == KeyEventType.KeyDown &&
                            !event.isAltPressed &&
                            !event.isCtrlPressed &&
                            !event.isMetaPressed
                        ) {
                            entryFocusRequester.requestFocus()
                        }
                    }
                },
    ) {
        val mainWindow = viewModel.mainWindowUiState.collectAsState()
        val menuData: WarlockMenuData? by viewModel.menuData.collectAsState()
        val presets by viewModel.presets.collectAsState(emptyMap())
        val defaultStyle = presets["default"] ?: SAFE_DEFAULT_STYLE
        val openWindows by viewModel.openWindows.collectAsState(emptyList())
        val progressBarSettings by viewModel.progressBarSettings.collectAsState()

        CompositionLocalProvider(
            LocalProgressBarSettings provides
                ProgressBarSettingsState(
                    settings = progressBarSettings,
                    saveColors = viewModel::saveProgressBarColors,
                    saveFont = viewModel::saveProgressBarFont,
                ),
            LocalWindowFindController provides viewModel.windowFindController,
        ) {
            Row(modifier = Modifier.weight(1f)) {
                if (sideBarVisible) {
                    val windows by viewModel.windows.collectAsState()
                    val scope = rememberCoroutineScope()
                    val sidebarBackground =
                        defaultStyle.backgroundColor.takeIf { it.isSpecified() }?.toColor()
                            ?: gameChrome.panelAlt
                    val sidebarTextColor =
                        defaultStyle.textColor.takeIf { it.isSpecified() }?.toColor()
                            ?: gameChrome.textPrimary
                    WarlockScrollableColumn(
                        modifier =
                            Modifier
                                .padding(2.dp)
                                .fillMaxHeight()
                                .width(240.dp)
                                .background(
                                    color = sidebarBackground,
                                    shape = RoundedCornerShape(2.dp),
                                ).border(
                                    width = Dp.Hairline,
                                    color = gameChrome.border,
                                    shape = RoundedCornerShape(2.dp),
                                ),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        windows.sortedBy { it.title }.forEach { window ->
                            DesktopWindowListItem(
                                color = sidebarTextColor,
                                windowInfo = window,
                                isOpen = openWindows.contains(window.name),
                                onClick = { open ->
                                    scope.launch {
                                        if (open) {
                                            viewModel.openWindow(window.name)
                                        } else {
                                            viewModel.closeWindow(window.name)
                                        }
                                    }
                                },
                                onClear = { viewModel.clearStream(window.name) },
                            )
                        }
                    }
                }
                val leftWindows by viewModel.leftWindowUiStates.collectAsState()
                val rightWindows by viewModel.rightWindowUiStates.collectAsState()
                val topWindows by viewModel.topWindowUiStates.collectAsState()
                val bottomWindows by viewModel.bottomWindowUiStates.collectAsState()
                DesktopGameTextWindows(
                    modifier = Modifier.weight(1f).padding(2.dp),
                    leftWindowUiStates = leftWindows,
                    rightWindowUiStates = rightWindows,
                    topWindowUiStates = topWindows,
                    bottomWindowUiStates = bottomWindows,
                    mainWindowUiState = mainWindow.value,
                    defaultStyle = defaultStyle,
                    selectedWindow = viewModel.selectedWindow.collectAsState().value,
                    openWindows = openWindows,
                    topHeight = viewModel.topHeight.collectAsState(null).value,
                    bottomHeight = viewModel.bottomHeight.collectAsState(null).value,
                    leftWidth = viewModel.leftWidth.collectAsState(null).value,
                    rightWidth = viewModel.rightWidth.collectAsState(null).value,
                    menuData = menuData,
                    onActionClick = { action ->
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

                            else -> {
                                null
                            }
                        }
                    },
                    onWidthChange = { name, width -> viewModel.setWindowWidth(name, width) },
                    onHeightChange = { name, height -> viewModel.setWindowHeight(name, height) },
                    onSizeChange = viewModel::setLocationSize,
                    onDrop = { result ->
                        if (result.sourceLocation == result.target.location) {
                            viewModel.changeWindowPositions(
                                result.sourceLocation,
                                result.sourceIndex,
                                result.target.insertionIndex,
                            )
                        } else {
                            viewModel.moveWindowToPosition(
                                result.name,
                                result.target.location,
                                result.target.insertionIndex,
                            )
                        }
                    },
                    onCloseClick = viewModel::closeWindow,
                    saveStyle = viewModel::saveWindowStyle,
                    saveNameFilter = viewModel::saveWindowNameFilter,
                    onWindowSelect = viewModel::selectWindow,
                    scrollEvents = viewModel.scrollEvents.collectAsState().value,
                    handledScrollEvent = viewModel::handledScrollEvent,
                    clearStream = viewModel::clearStream,
                )
            }
            DesktopGameBottomBar(viewModel, entryFocusRequester)
        }
    }
    val macroError by viewModel.macroError.collectAsState()
    if (macroError != null) {
        WarlockAlertDialog(
            title = "Macro error",
            text = macroError!!,
            onDismissRequest = { viewModel.handledMacroError() },
            confirmButton = {
                WarlockButton(onClick = { viewModel.handledMacroError() }, text = "OK")
            },
        )
    }
}

@Composable
private fun DesktopWindowListItem(
    color: Color,
    windowInfo: WindowInfo,
    isOpen: Boolean,
    onClick: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    // Per the design: a leading status dot (filled accent when shown, hollow + dimmed when hidden)
    // replaces the old eye icons, and a trailing "..." opens the per-window menu. Clicking the row
    // still toggles the window open/closed.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = { onClick(!isOpen) })
                .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier.size(12.dp),
            painter = painterResource(if (isOpen) Res.drawable.circle_filled else Res.drawable.circle),
            colorFilter =
                ColorFilter.tint(
                    if (isOpen) gameChrome.accentSubtle else gameChrome.textFaint,
                ),
            contentDescription = if (isOpen) "Shown" else "Hidden",
        )
        Spacer(Modifier.width(10.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = windowInfo.title,
            color = if (isOpen) color else gameChrome.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        WindowMenuButton(
            tint = gameChrome.textFaint,
            horizontalAlignment = Alignment.End,
        ) { dismiss ->
            selectableItem(
                selected = false,
                onClick = {
                    dismiss()
                    onClick(!isOpen)
                },
            ) {
                Text(if (isOpen) "Hide window" else "Show window")
            }
            selectableItem(
                selected = false,
                onClick = {
                    dismiss()
                    onClear()
                },
            ) {
                Text("Clear window")
            }
        }
    }
}
