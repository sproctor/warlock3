package warlockfe.warlock3.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.FrameWindowScope
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.MainScreen
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.theme.AppTheme
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.ThemeSetting

@Composable
fun FrameWindowScope.WarlockApp(
    appContainer: AppContainer,
    gameState: GameState,
    newWindow: () -> Unit,
    showUpdateDialog: () -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    val themeSetting by appContainer.clientSettings.observeTheme().collectAsState(ThemeSetting.AUTO)
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
            val characterId = when (val screen = gameState.screen) {
                is GameScreen.ConnectedGameState -> screen.viewModel.client.characterId.collectAsState().value
                else -> null
            }
            val isDisconnected = (gameState.screen as? GameScreen.ConnectedGameState)?.viewModel?.client?.disconnected?.collectAsState()
            AppMenuBar(
                characterId = characterId,
                isConnected = isDisconnected?.value != true,
                windowRepository = gameState.windowRepository,
                scriptEngineRegistry = appContainer.scriptManager,
                newWindow = newWindow,
                showSettings = { showSettings = true },
                disconnect = {
                    val screen = gameState.screen
                    if (screen is GameScreen.ConnectedGameState) {
                        screen.viewModel.client.disconnect()
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
