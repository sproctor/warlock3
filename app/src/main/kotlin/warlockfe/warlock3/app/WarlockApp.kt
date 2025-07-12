package warlockfe.warlock3.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.FrameWindowScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.MainScreen
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.theme.AppTheme
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.window.Window

@Composable
fun FrameWindowScope.WarlockApp(
    appContainer: AppContainer,
    gameState: GameState,
    newWindow: () -> Unit,
    showUpdateDialog: () -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    val themeSetting by appContainer.clientSettings.observeTheme().collectAsState(ThemeSetting.AUTO)
    val scope = rememberCoroutineScope()
    AppTheme(
        useDarkTheme = when (themeSetting) {
            ThemeSetting.AUTO -> isSystemInDarkTheme()
            ThemeSetting.LIGHT -> false
            ThemeSetting.DARK -> true
        }
    ) {
        CompositionLocalProvider(
            LocalScrollbarStyle provides LocalScrollbarStyle.current.copy(
                hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        ) {
            var isConnected by remember { mutableStateOf(false) }
            var windows by remember { mutableStateOf<List<Window>>(emptyList()) }
            var openWindows by remember { mutableStateOf<Set<String>>(emptySet()) }
            LaunchedEffect(gameState.screen) {
                when (val screen = gameState.screen) {
                    is GameScreen.ConnectedGameState -> {
                        isConnected = true
                        screen.viewModel.windowRepository.windows
                            .onEach { windowsMap ->
                                windows = windowsMap.values.toList()
                            }
                            .launchIn(this)
                        screen.viewModel.windowRepository.openWindows
                            .onEach {
                                openWindows = it
                            }
                            .launchIn(this)
                    }
                    else -> {
                        isConnected = false
                        windows = emptyList()
                        openWindows = emptySet()
                    }
                }
            }
            AppMenuBar(
                isConnected = isConnected,
                windows = windows,
                openWindows = openWindows,
                closeWindow = {
                    scope.launch {
                        (gameState.screen as? GameScreen.ConnectedGameState)?.viewModel?.windowRepository
                            ?.closeWindow(it)
                    }
                },
                openWindow = {
                    scope.launch {
                        (gameState.screen as? GameScreen.ConnectedGameState)?.viewModel?.windowRepository
                            ?.openWindow(it)
                    }
                },
                newWindow = newWindow,
                showSettings = { showSettings = true },
                disconnect = {
                    val screen = gameState.screen
                    if (screen is GameScreen.ConnectedGameState) {
                        scope.launch {
                            screen.viewModel.close()
                        }
                    }
                },
                runScript = {
                    val screen = gameState.screen
                    if (screen is GameScreen.ConnectedGameState) {
                        screen.viewModel.runScript(it)
                    }
                },
                showUpdateDialog = showUpdateDialog,
                warlockVersion = System.getProperty("app.version", "development")
            )

            var currentCharacter: GameCharacter? by remember { mutableStateOf(null) }

            MainScreen(
                sgeViewModelFactory = appContainer.sgeViewModelFactory,
                dashboardViewModelFactory = appContainer.dashboardViewModelFactory,
                gameState = gameState,
                updateCurrentCharacter = { currentCharacter = it }
            )

            if (showSettings) {
                SettingsDialog(
                    currentCharacter = currentCharacter,
                    closeDialog = { showSettings = false },
                    variableRepository = appContainer.variableRepository,
                    macroRepository = appContainer.macroRepository,
                    presetRepository = appContainer.presetRepository,
                    characterRepository = appContainer.characterRepository,
                    highlightRepository = appContainer.highlightRepository,
                    characterSettingsRepository = appContainer.characterSettingsRepository,
                    aliasRepository = appContainer.aliasRepository,
                    scriptDirRepository = appContainer.scriptDirRepository,
                    alterationRepository = appContainer.alterationRepository,
                    clientSettingRepository = appContainer.clientSettings,
                )
            }
        }
    }
}
