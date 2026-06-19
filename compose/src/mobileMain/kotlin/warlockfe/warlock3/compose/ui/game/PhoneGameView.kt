package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.explore
import warlockfe.warlock3.compose.ui.window.WindowView
import warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE
import warlockfe.warlock3.core.window.WindowLocation

/**
 * The phone (Compact/Medium) layout: an M3 top app bar, a status card (vitals + hands + condition
 * chips), secondary tabs over every window with one visible stream, and a command bar (assist chips
 * + entry + movement FAB) that opens the [MovementSheet].
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun PhoneGameView(
    viewModel: GameViewModel,
    entryFocusRequester: FocusRequester,
    navigateToDashboard: () -> Unit,
    openSettings: () -> Unit,
    openDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val character by viewModel.character.collectAsState(null)
    val mainWindow by viewModel.mainWindowUiState.collectAsState()
    val windows by viewModel.windows.collectAsState()
    val presets by viewModel.presets.collectAsState(emptyMap())
    val defaultStyle = presets["default"] ?: SAFE_DEFAULT_STYLE
    val openWindows by viewModel.openWindows.collectAsState(emptyList())
    val menuData by viewModel.menuData.collectAsState()
    val selectedWindow by viewModel.selectedWindow.collectAsState()
    var movementOpen by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf("main") }
    val currentTab = if (windows.any { it.name == selectedTab }) selectedTab else "main"

    Column(modifier) {
        GameTopBar(
            title = character?.name ?: "Warlock",
            subtitle = mainWindow.windowInfo.value?.subtitle,
            onMenu = openDrawer,
            onSettings = openSettings,
            onDashboard = navigateToDashboard,
        )
        GameStatusCard(
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        StreamTabs(
            windows = windows,
            selected = currentTab,
            onSelect = {
                selectedTab = it
                viewModel.selectWindow(it)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        WindowView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            headerModifier = Modifier,
            uiState = viewModel.streamWindowUiState(currentTab),
            location = WindowLocation.MAIN,
            defaultStyle = defaultStyle,
            isSelected = selectedWindow == currentTab,
            openWindows = openWindows,
            menuData = menuData,
            onActionClick = { action -> viewModel.onWindowAction(action) },
            onCloseClick = {},
            saveStyle = { viewModel.saveWindowStyle(currentTab, it) },
            saveNameFilter = { viewModel.saveWindowNameFilter(currentTab, it) },
            onSelect = { viewModel.selectWindow(currentTab) },
            scrollEvents = viewModel.scrollEvents.collectAsState().value,
            handledScrollEvent = viewModel::handledScrollEvent,
            clearStream = { viewModel.clearStream(currentTab) },
        )
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
