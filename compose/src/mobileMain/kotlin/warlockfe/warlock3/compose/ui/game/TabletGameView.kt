package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import warlockfe.warlock3.compose.components.ResizablePanel
import warlockfe.warlock3.compose.components.ResizablePanelState
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.more_vert
import warlockfe.warlock3.compose.ui.window.WindowView
import warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE
import warlockfe.warlock3.core.window.WindowLocation

/**
 * The tablet (Expanded) layout: the main window plus a resizable, tabbed secondary pane holding the
 * non-main windows. The pane's location (right/left/top/bottom, default right) is user-changeable and
 * persisted per character. Keeps the existing bottom-bar chrome.
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun TabletGameView(
    viewModel: GameViewModel,
    entryFocusRequester: FocusRequester,
    openSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val location by viewModel.observeTabletWindowLocation().collectAsState(WindowLocation.RIGHT)
    val windows by viewModel.windows.collectAsState()
    val hasSecondary = windows.any { it.name != "main" }

    Column(modifier) {
        Box(modifier = Modifier.weight(1f)) {
            if (!hasSecondary) {
                MainWindow(viewModel = viewModel, modifier = Modifier.fillMaxSize())
            } else {
                val isHorizontal = location == WindowLocation.LEFT || location == WindowLocation.RIGHT
                val handleBefore = location == WindowLocation.RIGHT || location == WindowLocation.BOTTOM
                val size =
                    when (location) {
                        WindowLocation.LEFT -> viewModel.leftWidth.collectAsState(null).value
                        WindowLocation.RIGHT -> viewModel.rightWidth.collectAsState(null).value
                        WindowLocation.TOP -> viewModel.topHeight.collectAsState(null).value
                        WindowLocation.BOTTOM -> viewModel.bottomHeight.collectAsState(null).value
                        else -> null
                    }
                val panelState =
                    remember(location, size == null) {
                        ResizablePanelState(initialSize = size?.dp ?: 320.dp, minSize = 120.dp)
                    }
                LaunchedEffect(panelState.currentSize, location) {
                    viewModel.setLocationSize(location, panelState.currentSize.value.toInt())
                }

                val pane: @Composable () -> Unit = {
                    ResizablePanel(
                        modifier = if (isHorizontal) Modifier.fillMaxHeight() else Modifier.fillMaxWidth(),
                        isHorizontal = isHorizontal,
                        handleBefore = handleBefore,
                        state = panelState,
                    ) {
                        SecondaryWindowPane(
                            viewModel = viewModel,
                            onChangeLocation = viewModel::setTabletWindowLocation,
                        )
                    }
                }

                if (isHorizontal) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (handleBefore) {
                            MainWindow(viewModel = viewModel, modifier = Modifier.weight(1f))
                            pane()
                        } else {
                            pane()
                            MainWindow(viewModel = viewModel, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (handleBefore) {
                            MainWindow(viewModel = viewModel, modifier = Modifier.weight(1f))
                            pane()
                        } else {
                            pane()
                            MainWindow(viewModel = viewModel, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        GameBottomBar(
            viewModel = viewModel,
            entryFocusRequester = entryFocusRequester,
            openSettings = openSettings,
        )
    }
}

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun MainWindow(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val mainWindow by viewModel.mainWindowUiState.collectAsState()
    val presets by viewModel.presets.collectAsState(emptyMap())
    val defaultStyle = presets["default"] ?: SAFE_DEFAULT_STYLE
    val openWindows by viewModel.openWindows.collectAsState(emptyList())
    val menuData by viewModel.menuData.collectAsState()
    val selectedWindow by viewModel.selectedWindow.collectAsState()
    WindowView(
        modifier = modifier,
        headerModifier = Modifier,
        uiState = mainWindow,
        location = WindowLocation.MAIN,
        defaultStyle = defaultStyle,
        isSelected = selectedWindow == mainWindow.name,
        openWindows = openWindows,
        menuData = menuData,
        onActionClick = { action -> viewModel.onWindowAction(action) },
        onCloseClick = {},
        saveStyle = { viewModel.saveWindowStyle(mainWindow.name, it) },
        saveNameFilter = { viewModel.saveWindowNameFilter(mainWindow.name, it) },
        onSelect = { viewModel.selectWindow(mainWindow.name) },
        scrollEvents = viewModel.scrollEvents.collectAsState().value,
        handledScrollEvent = viewModel::handledScrollEvent,
        clearStream = { viewModel.clearStream(mainWindow.name) },
    )
}

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun SecondaryWindowPane(
    viewModel: GameViewModel,
    onChangeLocation: (WindowLocation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val windows by viewModel.windows.collectAsState()
    val nonMain = remember(windows) { windows.filter { it.name != "main" }.sortedBy { it.title } }
    val presets by viewModel.presets.collectAsState(emptyMap())
    val defaultStyle = presets["default"] ?: SAFE_DEFAULT_STYLE
    val openWindows by viewModel.openWindows.collectAsState(emptyList())
    val menuData by viewModel.menuData.collectAsState()
    val selectedWindow by viewModel.selectedWindow.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf("") }
    val current = if (nonMain.any { it.name == selectedTab }) selectedTab else nonMain.firstOrNull()?.name

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StreamTabs(
                windows = nonMain,
                selected = current ?: "",
                onSelect = {
                    selectedTab = it
                    viewModel.selectWindow(it)
                },
                modifier = Modifier.weight(1f),
            )
            PaneLocationMenu(onChangeLocation = onChangeLocation)
        }
        if (current != null) {
            WindowView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                headerModifier = Modifier,
                uiState = viewModel.streamWindowUiState(current),
                location = WindowLocation.MAIN,
                defaultStyle = defaultStyle,
                isSelected = selectedWindow == current,
                openWindows = openWindows,
                menuData = menuData,
                onActionClick = { action -> viewModel.onWindowAction(action) },
                onCloseClick = {},
                saveStyle = { viewModel.saveWindowStyle(current, it) },
                saveNameFilter = { viewModel.saveWindowNameFilter(current, it) },
                onSelect = { viewModel.selectWindow(current) },
                scrollEvents = viewModel.scrollEvents.collectAsState().value,
                handledScrollEvent = viewModel::handledScrollEvent,
                clearStream = { viewModel.clearStream(current) },
            )
        }
    }
}

@Composable
private fun PaneLocationMenu(onChangeLocation: (WindowLocation) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painter = painterResource(Res.drawable.more_vert), contentDescription = "Pane location")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                "Dock right" to WindowLocation.RIGHT,
                "Dock left" to WindowLocation.LEFT,
                "Dock top" to WindowLocation.TOP,
                "Dock bottom" to WindowLocation.BOTTOM,
            ).forEach { (label, loc) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        onChangeLocation(loc)
                    },
                )
            }
        }
    }
}
