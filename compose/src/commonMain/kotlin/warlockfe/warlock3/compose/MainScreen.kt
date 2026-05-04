package warlockfe.warlock3.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.dashboard.DashboardView
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModelFactory
import warlockfe.warlock3.compose.ui.game.GameView
import warlockfe.warlock3.compose.ui.sge.SgeViewModelFactory
import warlockfe.warlock3.compose.ui.sge.SgeWizard
import warlockfe.warlock3.compose.util.SafeUriHandler
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.sge.SgeSettings

// TODO: use navigation here
@Suppress("ktlint:compose:vm-injection-check")
@Composable
fun MainScreen(
    sgeViewModelFactory: SgeViewModelFactory,
    dashboardViewModelFactory: DashboardViewModelFactory,
    gameState: GameState,
    updateCurrentCharacter: (GameCharacter?) -> Unit,
    sgeSettings: SgeSettings,
    sideBarVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    val uriHandler = LocalUriHandler.current
    CompositionLocalProvider(
        LocalUriHandler provides SafeUriHandler(uriHandler),
    ) {
        when (val screen = gameState.screen) {
            GameScreen.Dashboard -> {
                val viewModel =
                    viewModel {
                        dashboardViewModelFactory.create(gameState, sgeSettings)
                    }
                DashboardView(
                    viewModel = viewModel,
                    connectToSGE = {
                        scope.launch {
                            gameState.setScreen(GameScreen.NewGameState)
                        }
                    },
                    modifier = modifier,
                )
            }

            GameScreen.NewGameState -> {
                val viewModel =
                    remember {
                        sgeViewModelFactory.create(gameState, sgeSettings)
                    }
                SgeWizard(
                    viewModel = viewModel,
                    onCancel = {
                        scope.launch {
                            gameState.setScreen(GameScreen.Dashboard)
                        }
                    },
                    modifier = modifier,
                )
            }

            is GameScreen.ConnectedGameState -> {
                val character =
                    screen.viewModel.character
                        .collectAsState(null)
                        .value
                updateCurrentCharacter(character)
                GameView(
                    viewModel = screen.viewModel,
                    navigateToDashboard = {
                        scope.launch {
                            screen.viewModel.close()
                            gameState.setScreen(GameScreen.Dashboard)
                        }
                    },
                    sideBarVisible = sideBarVisible,
                    modifier = modifier,
                )
            }

            is GameScreen.ErrorState -> {
                Surface(modifier) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = screen.message)
                        Button(
                            onClick = {
                                scope.launch {
                                    gameState.setScreen(screen.returnTo)
                                }
                            },
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}
