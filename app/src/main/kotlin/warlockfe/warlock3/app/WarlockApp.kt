package warlockfe.warlock3.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.window.FrameWindowScope
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.MainScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.theme.AppTheme
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.sge.SimuGameCredentials

@Composable
fun FrameWindowScope.WarlockApp(
    appContainer: AppContainer,
    credentials: SimuGameCredentials?,
) {
    var showSettings by remember { mutableStateOf(false) }

    AppTheme {
        CompositionLocalProvider(
            LocalScrollbarStyle provides LocalScrollbarStyle.current.copy(
                hoverColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                unhoverColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
            )
        ) {
            val clipboardManager = LocalClipboardManager.current
            val gameState = remember {
                val initialGameState = if (credentials != null) {
                    val client =
                        appContainer.warlockClientFactory.createStormFrontClient(credentials)
                    client.connect()
                    val viewModel =
                        appContainer.gameViewModelFactory.create(client, clipboardManager)
                    GameState.ConnectedGameState(viewModel)
                } else {
                    GameState.Dashboard
                }
                mutableStateOf(initialGameState)
            }
            val characterId = when (val currentState = gameState.value) {
                is GameState.ConnectedGameState -> currentState.viewModel.client.characterId.collectAsState().value
                else -> null
            }
            AppMenuBar(
                characterId = characterId,
                windowRepository = appContainer.windowRepository,
                scriptEngineRegistry = appContainer.scriptManager,
                showSettings = { showSettings = true },
                disconnect = null,
                runScript = {
                    val currentGameState = gameState.value
                    if (currentGameState is GameState.ConnectedGameState) {
                        currentGameState.viewModel.runScript(it)
                    }
                },
                warlockVersion = System.getProperty("app.version", "development")
            )

            var currentCharacter: GameCharacter? by remember { mutableStateOf(null) }

            MainScreen(
                sgeViewModelFactory = appContainer.sgeViewModelFactory,
                dashboardViewModelFactory = appContainer.dashboardViewModelFactory,
                gameState = gameState.value,
                updateGameState = { gameState.value = it },
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
                )
            }
        }
    }
}
