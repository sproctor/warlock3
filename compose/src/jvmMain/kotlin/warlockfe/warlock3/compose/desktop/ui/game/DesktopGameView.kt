package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.ColorFilter
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
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockAlertDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.visibility_filled
import warlockfe.warlock3.compose.generated.resources.visibility_off_filled
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.prefs.repositories.defaultStyles
import warlockfe.warlock3.core.text.isSpecified
import warlockfe.warlock3.core.window.WindowInfo

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun DesktopGameView(
    viewModel: GameViewModel,
    navigateToDashboard: () -> Unit,
    sideBarVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val disconnected by viewModel.disconnected.collectAsState()
    val entryFocusRequester = remember { FocusRequester() }
    var ignoreNextUnknownKeyEvent by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground)
                .onPreviewKeyEvent { event ->
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
        if (disconnected) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .border(
                            width = 8.dp,
                            color = Color(red = 0xff, green = 0xcc, blue = 0),
                        ).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("You have been disconnected from the server")
                WarlockButton(
                    onClick = navigateToDashboard,
                    text = "Back to dashboard",
                )
            }
        }

        val mainWindow = viewModel.mainWindowUiState.collectAsState()
        val menuData: WarlockMenuData? by viewModel.menuData.collectAsState()
        val presets by viewModel.presets.collectAsState(emptyMap())
        val defaultStyle = presets["default"] ?: defaultStyles["default"]!!
        val openWindows by viewModel.openWindows.collectAsState(emptyList())

        Row(modifier = Modifier.weight(1f)) {
            if (sideBarVisible) {
                val windows by viewModel.windows.collectAsState()
                val scope = rememberCoroutineScope()
                val sidebarBackground =
                    defaultStyle.backgroundColor.takeIf { it.isSpecified() }?.toColor()
                        ?: JewelTheme.globalColors.panelBackground
                val sidebarTextColor =
                    defaultStyle.textColor.takeIf { it.isSpecified() }?.toColor()
                        ?: JewelTheme.globalColors.text.normal
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
                                color = JewelTheme.globalColors.borders.normal,
                                shape = RoundedCornerShape(2.dp),
                            ).padding(8.dp),
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
                        )
                    }
                }
            }
            val leftWindows by viewModel.leftWindowUiStates.collectAsState()
            val rightWindows by viewModel.rightWindowUiStates.collectAsState()
            val topWindows by viewModel.topWindowUiStates.collectAsState()
            val bottomWindows by viewModel.bottomWindowUiStates.collectAsState()
            // WindowView (main text pane) still M3 — fall through. Migrated in step 8.
            DesktopGameTextWindows(
                modifier = Modifier.weight(1f),
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
                        is WarlockAction.OpenMenu -> action.onClick()
                        else -> null
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
                onWindowSelect = viewModel::selectWindow,
                scrollEvents = viewModel.scrollEvents.collectAsState().value,
                handledScrollEvent = viewModel::handledScrollEvent,
                clearStream = viewModel::clearStream,
            )
        }
        DesktopGameBottomBar(viewModel, entryFocusRequester)
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
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = { onClick(!isOpen) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter =
                painterResource(
                    if (isOpen) {
                        Res.drawable.visibility_filled
                    } else {
                        Res.drawable.visibility_off_filled
                    },
                ),
            colorFilter = ColorFilter.tint(color),
            contentDescription = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(text = windowInfo.title, color = color)
    }
}
