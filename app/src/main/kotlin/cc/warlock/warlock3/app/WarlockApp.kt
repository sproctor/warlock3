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
import cc.warlock.warlock3.app.ui.game.GameView
import cc.warlock.warlock3.app.ui.game.GameViewModel
import cc.warlock.warlock3.app.ui.settings.SettingsDialog
import cc.warlock.warlock3.app.ui.sge.SgeViewModel
import cc.warlock.warlock3.app.ui.sge.SgeWizard
import cc.warlock.warlock3.core.client.GameCharacter

@Composable
fun WarlockApp(
    state: MutableState<GameState>,
    showSettings: Boolean,
    closeSettings: () -> Unit,
) {

    var currentCharacter: GameCharacter? by remember { mutableStateOf(null) }

    when (val currentState = state.value) {
        GameState.Dashboard -> {
            val clipboardManager = LocalClipboardManager.current
            val viewModel = remember {
                AppContainer.dashboardViewModelFactory(
                    updateGameState = { state.value = it },
                    clipboardManager = clipboardManager,
                )
            }
            DashboardView(
                viewModel = viewModel,
                connectToSGE = { state.value = GameState.NewGameState }
            )
        }
        GameState.NewGameState -> {
            val clipboardManager = LocalClipboardManager.current
            val viewModel = remember {
                SgeViewModel(
                    clientSettingRepository = AppContainer.clientSettings,
                    accountRepository = AppContainer.accountRepository,
                    clipboardManager = clipboardManager,
                    updateGameState = { gameState ->
                        state.value = gameState
                    },
                )
            }
            SgeWizard(viewModel = viewModel, onCancel = { state.value = GameState.Dashboard })
        }
        is GameState.ConnectedGameState -> {
            val character = currentState.viewModel.character.collectAsState(null).value
            if (currentCharacter != character) {
                currentCharacter = character
            }
            GameView(
                viewModel = currentState.viewModel,
                navigateToDashboard = {
                    state.value = GameState.Dashboard
                }
            )
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
            characterSettingsRepository = AppContainer.characterSettingsRepository,
            aliasRepository = AppContainer.aliasRepository,
        )
    }
}

sealed class GameState {
    object Dashboard : GameState()
    object NewGameState : GameState()
    data class ConnectedGameState(val viewModel: GameViewModel) : GameState()

    data class ErrorState(val message: String) : GameState()
}