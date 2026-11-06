package warlockfe.warlock3.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModel
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.sge.SgeViewModel
import warlockfe.warlock3.compose.util.SafeUriHandler
import warlockfe.warlock3.core.client.GameCharacter

/**
 * The shared app navigation host for both desktop and mobile. The current [GameState.screen] is
 * mapped to a Navigation 3 back stack and rendered with a [NavDisplay] (real routed navigation with
 * transitions and per-destination ViewModel scoping). The platform-specific screen UIs are supplied
 * as content slots, so this host owns the navigation, view-model creation and transition wiring
 * while each platform renders its own dashboard / wizard / game / error composables.
 *
 * The live connection stays in [GameState] (the [GameScreen.ConnectedGameState]); the [gameContent]
 * slot reads it via the [GameViewModel] handed to it.
 */
@Suppress("ktlint:compose:vm-injection-check")
@Composable
fun MainScreen(
    viewModels: MainScreenViewModels,
    gameState: GameState,
    updateCurrentCharacter: (GameCharacter?) -> Unit,
    dashboardContent: @Composable (viewModel: DashboardViewModel, connectToSge: () -> Unit) -> Unit,
    wizardContent: @Composable (viewModel: SgeViewModel, onCancel: () -> Unit) -> Unit,
    gameContent: @Composable (viewModel: GameViewModel, navigateToDashboard: () -> Unit) -> Unit,
    errorContent: @Composable (message: String, onDismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    CompositionLocalProvider(
        LocalUriHandler provides SafeUriHandler(uriHandler),
    ) {
        val backStack: List<NavKey> =
            when (val screen = gameState.screen) {
                GameScreen.Dashboard -> listOf(DashboardKey)
                GameScreen.NewGameState -> listOf(DashboardKey, WizardKey)
                is GameScreen.ConnectedGameState -> listOf(GameKey)
                is GameScreen.ErrorState -> listOf(DashboardKey, ErrorKey(screen.message))
            }
        NavDisplay(
            backStack = backStack,
            modifier = modifier,
            // The view-model decorator gives each destination its own store, cleared when the
            // destination leaves the back stack: backing out of the wizard closes its SGE
            // connection, and the dashboard's work stops while a game is on screen.
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            onBack = {
                scope.launch {
                    when (val screen = gameState.screen) {
                        GameScreen.NewGameState -> {
                            gameState.setScreen(GameScreen.Dashboard)
                        }

                        is GameScreen.ErrorState -> {
                            gameState.setScreen(screen.returnTo)
                        }

                        else -> {} // Dashboard / connected game: no in-app back target
                    }
                }
            },
            entryProvider =
                entryProvider {
                    entry<DashboardKey> {
                        val viewModel =
                            viewModel {
                                viewModels.dashboard(gameState)
                            }
                        dashboardContent(viewModel) {
                            scope.launch { gameState.setScreen(GameScreen.NewGameState) }
                        }
                    }
                    entry<WizardKey> {
                        val viewModel =
                            viewModel {
                                viewModels.wizard(gameState)
                            }
                        wizardContent(viewModel) {
                            scope.launch { gameState.setScreen(GameScreen.Dashboard) }
                        }
                    }
                    entry<GameKey> {
                        val connected = gameState.screen as? GameScreen.ConnectedGameState
                        if (connected != null) {
                            val character =
                                connected.viewModel.character
                                    .collectAsState(null)
                                    .value
                            updateCurrentCharacter(character)
                            gameContent(connected.viewModel) {
                                scope.launch {
                                    connected.viewModel.close()
                                    gameState.setScreen(GameScreen.Dashboard)
                                }
                            }
                        }
                    }
                    entry<ErrorKey> { key ->
                        val returnTo =
                            (gameState.screen as? GameScreen.ErrorState)?.returnTo ?: GameScreen.Dashboard
                        errorContent(key.message) {
                            scope.launch { gameState.setScreen(returnTo) }
                        }
                    }
                },
        )
    }
}
