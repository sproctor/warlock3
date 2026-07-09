package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.circle
import warlockfe.warlock3.compose.generated.resources.circle_filled
import warlockfe.warlock3.compose.util.LocalBaseStyle
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.text.isSpecified
import warlockfe.warlock3.core.window.WindowInfo

/**
 * The Large/Extra-large layout: the original drag-and-drop docking (sidebar window list + docked
 * top/bottom/left/right windows + the dense bottom bar). Unchanged behavior.
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun LargeGameView(
    viewModel: GameViewModel,
    entryFocusRequester: FocusRequester,
    openSettings: () -> Unit,
    navigateToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var windowListVisible by remember { mutableStateOf(false) }
    Column(modifier) {
        val mainWindow = viewModel.mainWindowUiState.collectAsState()
        val menuData: WarlockMenuData? by viewModel.menuData.collectAsState()
        val defaultStyle = LocalBaseStyle.current
        val openWindows by viewModel.openWindows.collectAsState(emptyList())
        val character by viewModel.character.collectAsState(null)
        val disconnected by viewModel.disconnected.collectAsState()

        LargeGameTopBar(
            title = character?.name ?: "Warlock",
            subtitle =
                listOfNotNull(
                    character?.gameCode?.takeIf { it.isNotBlank() },
                    mainWindow.value.windowInfo.value
                        ?.subtitle
                        ?.takeIf { it.isNotBlank() },
                ).joinToString(separator = " - ").ifBlank { null },
            connected = !disconnected,
            canReconnect = viewModel.canReconnect,
            onReconnect = viewModel::reconnect,
            onMenu = { windowListVisible = !windowListVisible },
            onSettings = openSettings,
            onDashboard = navigateToDashboard,
        )

        Row(modifier = Modifier.weight(1f)) {
            if (windowListVisible) {
                val windows by viewModel.windows.collectAsState()
                val scope = rememberCoroutineScope()
                ScrollableColumn(
                    Modifier
                        .padding(2.dp)
                        .fillMaxHeight()
                        .width(240.dp)
                        .background(
                            color =
                                defaultStyle.backgroundColor.takeIf { it.isSpecified() }?.toColor()
                                    ?: MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.extraSmall,
                        ).border(
                            width = Dp.Hairline,
                            color = MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.extraSmall,
                        ).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    windows.sortedBy { it.title }.forEach { window ->
                        WindowListItem(
                            color =
                                defaultStyle.textColor.takeIf { it.isSpecified() }?.toColor()
                                    ?: MaterialTheme.colorScheme.onSurface,
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
            GameTextWindows(
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
                onActionClick = { action -> viewModel.onWindowAction(action) },
                onWidthChange = { name, width -> viewModel.setWindowWidth(name, width) },
                onHeightChange = { name, height -> viewModel.setWindowHeight(name, height) },
                onSizeChange = viewModel::setLocationSize,
                onDrop = { result -> viewModel.onWindowDrop(result) },
                onCloseClick = viewModel::closeWindow,
                onOpenWindowSettings = viewModel::requestEditWindowSettings,
                onWindowSelect = viewModel::selectWindow,
                scrollEvents = viewModel.scrollEvents.collectAsState().value,
                handledScrollEvent = viewModel::handledScrollEvent,
                clearStream = viewModel::clearStream,
            )
        }
        // Settings and the window-list toggle live in the top app bar, so the bottom bar here is
        // just the entry / vitals / hands / indicators / compass.
        GameBottomBar(
            viewModel = viewModel,
            entryFocusRequester = entryFocusRequester,
        )
    }
}

@Composable
private fun WindowListItem(
    color: Color,
    windowInfo: WindowInfo,
    isOpen: Boolean,
    onClick: (Boolean) -> Unit,
) {
    // A filled accent dot when shown, a hollow dimmed ring when hidden (matching the desktop list).
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = { onClick(!isOpen) })
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(12.dp),
            painter = painterResource(if (isOpen) Res.drawable.circle_filled else Res.drawable.circle),
            tint = if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            contentDescription = if (isOpen) "Shown" else "Hidden",
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = windowInfo.title,
            color = if (isOpen) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
