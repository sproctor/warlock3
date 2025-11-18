package warlockfe.warlock3.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.components.CompassTheme
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.compass_main
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.dashboard.DashboardView
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModelFactory
import warlockfe.warlock3.compose.ui.game.GameView
import warlockfe.warlock3.compose.ui.sge.SgeViewModelFactory
import warlockfe.warlock3.compose.ui.sge.SgeWizard
import warlockfe.warlock3.compose.util.LocalCompassTheme
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.loadCompassTheme
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.sge.SgeSettings

// TODO: use navigation here
@Composable
fun MainScreen(
    sgeViewModelFactory: SgeViewModelFactory,
    dashboardViewModelFactory: DashboardViewModelFactory,
    gameState: GameState,
    updateCurrentCharacter: (GameCharacter?) -> Unit,
    sgeSettings: SgeSettings,
) {
    var compassTheme by remember {
        mutableStateOf(
            CompassTheme(
                background = Res.drawable.compass_main,
                directions = emptyMap()
            )
        )
    }
    val skin = LocalSkin.current
    LaunchedEffect(skin) {
        compassTheme = loadCompassTheme(skin)
    }
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(
        LocalCompassTheme provides compassTheme,
    ) {
        when (val screen = gameState.screen) {
            GameScreen.Dashboard -> {
                val viewModel = viewModel {
                    dashboardViewModelFactory.create(gameState, sgeSettings)
                }
                DashboardView(
                    viewModel = viewModel,
                    connectToSGE = {
                        scope.launch {
                            gameState.setScreen(GameScreen.NewGameState)
                        }
                    }
                )
            }

            GameScreen.NewGameState -> {
                val viewModel = remember {
                    sgeViewModelFactory.create(gameState, sgeSettings)
                }
                SgeWizard(
                    viewModel = viewModel,
                    onCancel = {
                        scope.launch {
                            gameState.setScreen(GameScreen.Dashboard)
                        }
                    }
                )
            }

            is GameScreen.ConnectedGameState -> {
                val character = screen.viewModel.character.collectAsState(null).value
                updateCurrentCharacter(character)
                GameView(
                    viewModel = screen.viewModel,
                    navigateToDashboard = {
                        scope.launch {
                            screen.viewModel.close()
                            gameState.setScreen(GameScreen.Dashboard)
                        }
                    }
                )
            }

            is GameScreen.ErrorState -> {
                Surface {
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
                            }
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}