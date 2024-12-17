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
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.dashboard.DashboardView
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModelFactory
import warlockfe.warlock3.compose.ui.game.GameView
import warlockfe.warlock3.compose.ui.sge.SgeViewModelFactory
import warlockfe.warlock3.compose.ui.sge.SgeWizard
import warlockfe.warlock3.core.client.GameCharacter

@Composable
fun MainScreen(
    sgeViewModelFactory: SgeViewModelFactory,
    dashboardViewModelFactory: DashboardViewModelFactory,
    gameState: GameState,
    updateCurrentCharacter: (GameCharacter?) -> Unit,
) {

    when (val screen = gameState.screen) {
        GameScreen.Dashboard -> {
            val viewModel = remember {
                dashboardViewModelFactory.create(gameState)
            }
            DashboardView(
                viewModel = viewModel,
                connectToSGE = { gameState.screen = GameScreen.NewGameState }
            )
        }

        GameScreen.NewGameState -> {
            val viewModel = remember {
                sgeViewModelFactory.create(gameState)
            }
            SgeWizard(viewModel = viewModel, onCancel = { gameState.screen = GameScreen.Dashboard })
        }

        is GameScreen.ConnectedGameState -> {
            val character = screen.viewModel.character.collectAsState(null).value
            updateCurrentCharacter(character)
            GameView(
                viewModel = screen.viewModel,
                navigateToDashboard = {
                    gameState.screen = GameScreen.Dashboard
                }
            )
        }

        is GameScreen.ErrorState -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = screen.message)
                Button(
                    onClick = { gameState.screen = GameScreen.NewGameState }
                ) {
                    Text("OK")
                }
            }
        }
    }
}