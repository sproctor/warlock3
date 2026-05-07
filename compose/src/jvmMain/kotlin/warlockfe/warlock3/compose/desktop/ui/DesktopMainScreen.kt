package warlockfe.warlock3.compose.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.ui.dashboard.DesktopDashboardView
import warlockfe.warlock3.compose.desktop.ui.sge.DesktopSgeWizard
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModelFactory
import warlockfe.warlock3.compose.ui.game.GameView
import warlockfe.warlock3.compose.ui.sge.SgeViewModelFactory
import warlockfe.warlock3.compose.util.SafeUriHandler
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.sge.SgeSettings

@Suppress("ktlint:compose:vm-injection-check")
@Composable
fun DesktopMainScreen(
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
                DesktopDashboardView(
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
                DesktopSgeWizard(
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
                // GameView still uses M3 in commonMain — fall through. Will be migrated
                // in step 5 (main game window chrome) and step 8 (main text pane).
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
                Column(
                    modifier =
                        modifier
                            .fillMaxSize()
                            .background(JewelTheme.globalColors.panelBackground),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = screen.message)
                    WarlockButton(
                        onClick = {
                            scope.launch { gameState.setScreen(screen.returnTo) }
                        },
                        text = "OK",
                    )
                }
            }
        }
    }
}
