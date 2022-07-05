package cc.warlock.warlock3.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import cc.warlock.warlock3.app.di.AppContainer
import cc.warlock.warlock3.app.ui.dashboard.DashboardView
import cc.warlock.warlock3.app.ui.dashboard.DashboardViewModel
import cc.warlock.warlock3.app.ui.game.GameView
import cc.warlock.warlock3.app.ui.window.WindowViewModel
import cc.warlock.warlock3.app.ui.settings.SettingsDialog
import cc.warlock.warlock3.app.ui.sge.SgeViewModel
import cc.warlock.warlock3.app.ui.sge.SgeWizard
import cc.warlock.warlock3.core.prefs.models.GameCharacter
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.net.UnknownHostException

@Composable
fun WarlockApp(
    state: MutableState<GameState>,
    showSettings: Boolean,
    closeSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var currentCharacter: GameCharacter? by remember { mutableStateOf(null) }

    when (val currentState = state.value) {
        GameState.Dashboard -> {
            val viewModel = remember {
                AppContainer.dashboardViewModelFactory { gameState -> state.value = gameState }
            }
            DashboardView(
                viewModel = viewModel,
                connectToSGE = { state.value = GameState.NewGameState }
            )
        }
        GameState.NewGameState -> {
            val viewModel = remember {
                SgeViewModel(
                    clientSettingRepository = AppContainer.clientSettings,
                    accountRepository = AppContainer.accountRepository,
                    readyToPlay = { gameState ->
                        state.value = gameState
                    },
                )
            }
            SgeWizard(viewModel = viewModel, onCancel = { state.value = GameState.Dashboard })
        }
        is GameState.ConnectedGameState -> {
            val client = remember(currentState.key) {
                currentCharacter = currentState.character
                scope.launch {
                    AppContainer.characterRepository.saveCharacter(currentState.character)
                    AppContainer.clientSettings.put("lastUsername", currentState.character.accountId)
                }
                try {
                    StormfrontClient(
                        host = currentState.host,
                        port = currentState.port,
                        windowRepository = AppContainer.windowRepository,
                        maxTypeAhead = 1,
                    ).apply {
                        connect(currentState.key)
                    }
                } catch (e: UnknownHostException) {
                    state.value = GameState.ErrorState("Unknown host: ${e.message}")
                    return
                }
            }
            val clipboard = LocalClipboardManager.current
            val viewModel = remember(client) {
                AppContainer.gameViewModelFactory(client, clipboard)
            }
            val windowViewModels = remember { mutableStateOf(emptyMap<String, WindowViewModel>()) }
            val windows by AppContainer.windowRepository.windows.collectAsState()
            windows.keys.forEach { name ->
                if (name != "main" && windowViewModels.value[name] == null) {
                    windowViewModels.value += name to WindowViewModel(
                        client = client,
                        name = name,
                        window = AppContainer.windowRepository.windows.map { it[name] },
                        highlightRepository = AppContainer.highlightRepository,
                        presetRepository = AppContainer.presetRepository,
                    )
                }
            }
            val mainWindowViewModel = remember {
                WindowViewModel(
                    name = "main",
                    client = client,
                    window = AppContainer.windowRepository.windows.map { it["main"] },
                    highlightRepository = AppContainer.highlightRepository,
                    presetRepository = AppContainer.presetRepository,
                )
            }
            GameView(viewModel = viewModel, windowViewModels.value, mainWindowViewModel)

        }
        is GameState.ErrorState -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = currentState.message)
                Button(
                    onClick = { state.value = GameState.NewGameState }
                ) {
                    Text("OK")
                }
            }
        }
    }
    if (showSettings) {
        SettingsDialog(
            currentCharacter = currentCharacter,
            closeDialog = closeSettings,
            variableRepository = AppContainer.variableRepository,
            macroRepository = AppContainer.macroRepository,
            presetRepository = AppContainer.presetRepository,
            characterRepository = AppContainer.characterRepository,
            highlightRepository = AppContainer.highlightRepository,
        )
    }
}

@Composable
fun rememberGameState(): MutableState<GameState> {
    return remember { mutableStateOf(GameState.Dashboard) }
}

sealed class GameState {
    object Dashboard : GameState()
    object NewGameState : GameState()
    data class ConnectedGameState(val host: String, val port: Int, val key: String, val character: GameCharacter) :
        GameState()

    data class ErrorState(val message: String) : GameState()
}