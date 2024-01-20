package warlockfe.warlock3.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import warlockfe.warlock3.android.di.AppContainer
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.dashboard.DashboardView
import warlockfe.warlock3.compose.ui.game.GameView
import warlockfe.warlock3.compose.ui.sge.SgeWizard
import warlockfe.warlock3.core.client.GameCharacter

@Composable
fun WarlockApp(
    appContainer: AppContainer,
    state: MutableState<GameState>,
    showSettings: Boolean,
    closeSettings: () -> Unit,
) {

    var currentCharacter: GameCharacter? by remember { mutableStateOf(null) }

    when (val currentState = state.value) {
        GameState.Dashboard -> {
            val clipboardManager = LocalClipboardManager.current
            val viewModel = remember {
                appContainer.dashboardViewModelFactory(
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
                appContainer.sgeViewModelFactory.create(
                    clipboardManager = clipboardManager,
                    updateGameState = { gameState ->
                        state.value = gameState
                    }
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
}