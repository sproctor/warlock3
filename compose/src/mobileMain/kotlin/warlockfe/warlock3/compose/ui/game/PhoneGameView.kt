package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberWideNavigationRailState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.explore
import warlockfe.warlock3.compose.ui.window.WindowView
import warlockfe.warlock3.compose.util.LocalBaseStyle
import warlockfe.warlock3.core.text.toStyleDefinition

/**
 * The phone (Compact/Medium) layout: an M3 top app bar, a status card (vitals + hands + condition
 * chips), the user's chosen windows as a reorderable tab strip with one visible stream, and a
 * command bar (assist chips + entry + movement FAB) that opens the [MovementSheet].
 *
 * A window becomes a tab from the [PhoneWindowRail], and stops being one by being dragged onto the
 * close target or through the selected tab's menu. None of that touches the windows' saved open
 * flags: those drive the docking layout, which this size never shows.
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun PhoneGameView(
    viewModel: GameViewModel,
    entryFocusRequester: FocusRequester,
    navigateToDashboard: () -> Unit,
    openSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val character by viewModel.character.collectAsState(null)
    val mainWindow by viewModel.mainWindowUiState.collectAsState()
    val tabbableWindows by viewModel.tabbableWindows.collectAsState()
    val tabs by viewModel.phoneTabs.collectAsState()
    val defaultStyle = LocalBaseStyle.current.toStyleDefinition()
    val openWindows by viewModel.openWindows.collectAsState(emptyList())
    val menuData by viewModel.menuData.collectAsState()
    val selectedWindow by viewModel.selectedWindow.collectAsState()
    val disconnected by viewModel.disconnected.collectAsState()
    var movementOpen by remember { mutableStateOf(false) }
    val railState = rememberWideNavigationRailState()
    val tabDrag = remember { TabDragState() }
    val scope = rememberCoroutineScope()
    // Derived rather than held locally: the view model already owns the selection, and a tab can
    // leave the strip under us. Coercing here is what keeps a removed tab from leaving a blank body.
    val currentTab = if (selectedWindow in tabs) selectedWindow else MAIN_WINDOW_NAME
    // The strip falls back to main whenever the selection leaves it - a tab that was removed, or a
    // window the game closed. Put the view model back in step, or find-in-window and the selected
    // styling go on pointing at a window that is no longer on screen.
    LaunchedEffect(currentTab, selectedWindow) {
        if (selectedWindow != currentTab) viewModel.selectWindow(currentTab)
    }

    Column(modifier) {
        GameTopBar(
            title = character?.name ?: "Warlock",
            subtitle = mainWindow.windowInfo.value?.subtitle,
            onMenu = { scope.launch { railState.expand() } },
            onSettings = openSettings,
            onDashboard = navigateToDashboard,
            disconnected = disconnected,
            canReconnect = viewModel.canReconnect,
            onReconnect = viewModel::reconnect,
        )
        GameStatusCard(
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        PhoneTabStrip(
            windows = tabbableWindows,
            tabs = tabs,
            selected = currentTab,
            drag = tabDrag,
            onSelect = viewModel::selectWindow,
            onMove = viewModel::movePhoneTab,
            onClose = { name -> closePhoneTab(viewModel, name, tabs, currentTab) },
            onOpenWindowSettings = viewModel::requestEditWindowSettings,
            onClearWindow = viewModel::clearStream,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            WindowView(
                modifier = Modifier.fillMaxSize(),
                headerModifier = Modifier,
                uiState = viewModel.streamWindowUiState(currentTab),
                canHide = false,
                defaultStyle = defaultStyle,
                isSelected = selectedWindow == currentTab,
                openWindows = openWindows,
                menuData = menuData,
                onActionClick = { action -> viewModel.onWindowAction(action) },
                onCloseClick = {},
                onOpenWindowSettings = { viewModel.requestEditWindowSettings(currentTab) },
                onSelect = { viewModel.selectWindow(currentTab) },
                scrollEvents = viewModel.scrollEvents.collectAsState().value,
                handledScrollEvent = viewModel::handledScrollEvent,
                clearStream = { viewModel.clearStream(currentTab) },
            )
            // Hidden while the main window is the one being dragged: it is the one tab that cannot
            // be removed, so offering a target it would refuse is worse than offering none.
            if (tabDrag.draggingTab.let { it != null && it != MAIN_WINDOW_NAME }) {
                TabCloseTarget(
                    active = tabDrag.isOverCloseTarget,
                    onBounds = { tabDrag.closeTargetBounds = it },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                )
            }
        }
        PhoneCommandBar(
            viewModel = viewModel,
            entryFocusRequester = entryFocusRequester,
            onMovement = { movementOpen = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    if (movementOpen) {
        MovementSheet(
            directions = viewModel.compassState.collectAsState().value,
            onMove = viewModel::sendCommand,
            onDismiss = { movementOpen = false },
        )
    }
    PhoneWindowRail(
        state = railState,
        windows = tabbableWindows,
        tabs = tabs,
        onAdd = viewModel::addPhoneTab,
        onSelect = viewModel::selectWindow,
    )
}

/** Takes a tab out of the strip, moving the selection to a neighbour when it was the one shown. */
private fun closePhoneTab(
    viewModel: GameViewModel,
    name: String,
    tabs: List<String>,
    current: String,
) {
    if (name == current) {
        val index = tabs.indexOf(name)
        val next = tabs.getOrNull(index + 1) ?: tabs.getOrNull(index - 1) ?: MAIN_WINDOW_NAME
        viewModel.selectWindow(next)
    }
    viewModel.removePhoneTab(name)
}

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun PhoneCommandBar(
    viewModel: GameViewModel,
    entryFocusRequester: FocusRequester,
    onMovement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionBar by viewModel.actionBar.collectAsState()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WarlockEntry(
                viewModel = viewModel,
                entryFocusRequester = entryFocusRequester,
                modifier = Modifier.weight(1f),
            )
            FloatingActionButton(onClick = onMovement) {
                Icon(painter = painterResource(Res.drawable.explore), contentDescription = "Movement")
            }
        }
    }
}
