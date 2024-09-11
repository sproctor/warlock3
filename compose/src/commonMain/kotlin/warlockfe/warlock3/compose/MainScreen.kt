package warlockfe.warlock3.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.dashboard.DashboardView
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModelFactory
import warlockfe.warlock3.compose.ui.game.GameView
import warlockfe.warlock3.compose.ui.sge.SgeViewModelFactory
import warlockfe.warlock3.compose.ui.sge.SgeWizard
import warlockfe.warlock3.core.client.GameCharacter

@Composable
expect fun Example()

@Composable
fun MainScreen(
    sgeViewModelFactory: SgeViewModelFactory,
    dashboardViewModelFactory: DashboardViewModelFactory,
    gameState: GameState,
    updateGameState: (GameState) -> Unit,
    updateCurrentCharacter: (GameCharacter?) -> Unit,
) {
    Example()
    when (gameState) {
        GameState.Dashboard -> {
            val clipboardManager = LocalClipboardManager.current
            val viewModel = remember {
                dashboardViewModelFactory.create(
                    updateGameState = updateGameState,
                    clipboardManager = clipboardManager,
                )
            }
            DashboardView(
                viewModel = viewModel,
                connectToSGE = { updateGameState(GameState.NewGameState) }
            )
        }

        GameState.NewGameState -> {
            val clipboardManager = LocalClipboardManager.current
            val viewModel = remember {
                sgeViewModelFactory.create(
                    clipboardManager = clipboardManager,
                    updateGameState = updateGameState
                )
            }
            SgeWizard(viewModel = viewModel, onCancel = { updateGameState(GameState.Dashboard) })
        }

        is GameState.ConnectedGameState -> {
            val character = gameState.viewModel.character.collectAsState(null).value
            updateCurrentCharacter(character)
            GameView(
                viewModel = gameState.viewModel,
                navigateToDashboard = {
                    updateGameState(GameState.Dashboard)
                }
            )
        }

        is GameState.ErrorState -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = gameState.message)
                Button(
                    onClick = { updateGameState(GameState.NewGameState) }
                ) {
                    Text("OK")
                }
            }
        }
    }
}