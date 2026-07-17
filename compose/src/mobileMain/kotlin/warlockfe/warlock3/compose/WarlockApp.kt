package warlockfe.warlock3.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.menu
import warlockfe.warlock3.compose.generated.resources.settings_filled
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.ui.dashboard.DashboardView
import warlockfe.warlock3.compose.ui.game.GameView
import warlockfe.warlock3.compose.ui.settings.SettingsPage
import warlockfe.warlock3.compose.ui.settings.SettingsScreen
import warlockfe.warlock3.compose.ui.settings.WindowSettingsLiveContext
import warlockfe.warlock3.compose.ui.sge.SgeWizard
import warlockfe.warlock3.compose.ui.theme.AppTheme
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.sge.SgeSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarlockApp(
    appContainer: AppContainer,
    sgeSettings: SgeSettings,
    modifier: Modifier = Modifier,
) {
    val themeSetting by appContainer.clientSettings.observeTheme().collectAsState(ThemeSetting.AUTO)
    val darkMode =
        when (themeSetting) {
            ThemeSetting.AUTO -> isSystemInDarkTheme()
            ThemeSetting.LIGHT -> false
            ThemeSetting.DARK -> true
        }
    LaunchedEffect(darkMode) { appContainer.darkMode.value = darkMode }
    AppTheme(useDarkTheme = darkMode) {
        // Held in a retained ViewModel so the navigation state and live connection survive
        // configuration changes (e.g. rotation).
        val gameState = viewModel { AppViewModel() }.gameState
        var showSettings by remember { mutableStateOf(false) }
        // Where the settings screen opens (null = the category list), plus the window whose editor to
        // focus. Set by a game window's "Window settings" action; reset when opened from the menu.
        var settingsInitialPage: SettingsPage? by remember { mutableStateOf(null) }
        var settingsWindowTarget: String? by remember { mutableStateOf(null) }
        var currentCharacter: GameCharacter? by remember { mutableStateOf(null) }

        val gameViewModel = (gameState.screen as? GameScreen.ConnectedGameState)?.viewModel
        val connectedCharacterId by
            remember(gameViewModel) { gameViewModel?.connectedCharacterId ?: MutableStateFlow(null) }
                .collectAsState()
        // Live window state for the Appearance -> Windows section; null when nothing is connected.
        val windowLiveContext =
            remember(gameViewModel, connectedCharacterId) {
                val cid = connectedCharacterId
                if (gameViewModel != null && cid != null) {
                    WindowSettingsLiveContext(
                        connectedCharacterId = cid,
                        windowInfo = gameViewModel.windows,
                        openWindow = gameViewModel::openWindow,
                        closeWindow = gameViewModel::closeWindow,
                    )
                } else {
                    null
                }
            }
        // A game window's "Window settings" action opens the settings screen to its editor. Kept at the
        // app root so it stays subscribed while the settings screen is shown.
        LaunchedEffect(gameViewModel) {
            gameViewModel?.editWindowSettingsRequests?.collect { name ->
                settingsInitialPage = SettingsPage.Windows
                settingsWindowTarget = name
                showSettings = true
            }
        }

        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            modifier = modifier,
            drawerContent = {
                ModalDrawerSheet {
                    DrawerContent(
                        openSettings = {
                            scope.launch { drawerState.close() }
                            settingsInitialPage = null
                            settingsWindowTarget = null
                            showSettings = true
                        },
                    )
                }
            },
        ) {
            if (showSettings) {
                SettingsScreen(
                    appContainer = appContainer,
                    currentCharacter = currentCharacter,
                    onClose = { showSettings = false },
                    initialPage = settingsInitialPage,
                    initialWindowTarget = settingsWindowTarget,
                    windowLiveContext = windowLiveContext,
                )
            } else {
                Scaffold(
                    topBar = {
                        // The connected game is chromeless; settings are reached from its bottom bar.
                        if (gameState.screen !is GameScreen.ConnectedGameState) {
                            WarlockTopBar(openDrawer = { scope.launch { drawerState.open() } })
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        MainScreen(
                            sgeViewModelFactory = appContainer.sgeViewModelFactory,
                            dashboardViewModelFactory = appContainer.dashboardViewModelFactory,
                            gameState = gameState,
                            updateCurrentCharacter = { currentCharacter = it },
                            sgeSettings = sgeSettings,
                            dashboardContent = { viewModel, connectToSge ->
                                DashboardView(viewModel = viewModel, connectToSGE = connectToSge)
                            },
                            wizardContent = { viewModel, onCancel ->
                                SgeWizard(viewModel = viewModel, onCancel = onCancel)
                            },
                            gameContent = { viewModel, navigateToDashboard ->
                                GameView(
                                    viewModel = viewModel,
                                    navigateToDashboard = navigateToDashboard,
                                    openSettings = {
                                        settingsInitialPage = null
                                        settingsWindowTarget = null
                                        showSettings = true
                                    },
                                    openDrawer = { scope.launch { drawerState.open() } },
                                )
                            },
                            errorContent = { message, onDismiss ->
                                ErrorScreen(message = message, onDismiss = onDismiss)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    openSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableColumn(modifier) {
        NavigationDrawerItem(
            modifier = Modifier.padding(horizontal = 12.dp),
            label = { Text("Settings") },
            icon = {
                Icon(
                    painter = painterResource(Res.drawable.settings_filled),
                    contentDescription = null,
                )
            },
            onClick = openSettings,
            selected = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarlockTopBar(openDrawer: () -> Unit) {
    TopAppBar(
        title = { Text(text = "Warlock", maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = openDrawer) {
                Icon(
                    painter = painterResource(Res.drawable.menu),
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
private fun ErrorScreen(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = message)
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    }
}
