package warlockfe.warlock3.compose

import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModel
import warlockfe.warlock3.compose.ui.sge.SgeViewModel
import warlockfe.warlock3.core.sge.SgeSettings

/**
 * Makes the view models of a window's [MainScreen]: its dashboard and its new-game wizard, built
 * from [container]'s services, logging in through the login server [sgeSettings] names.
 */
class MainScreenViewModels(
    private val container: AppContainer,
    private val sgeSettings: SgeSettings,
) {
    /** The dashboard of the window showing [gameState]. */
    fun dashboard(gameState: GameState): DashboardViewModel =
        with(container) {
            DashboardViewModel(
                gameState = gameState,
                sgeSettings = sgeSettings,
                connectionRepository = connectionRepository,
                connectionSettingsRepository = connectionSettingsRepository,
                sgeClientFactory = sgeClientFactory,
                connectToGame = connectToGameUseCase,
                gameStarter = gameViewModelFactory.starter(gameState),
                clientSettingRepository = clientSettings,
                accountRepository = accountRepository,
                mudMobileApi = mudMobileApi,
                mudMobileConnect = mudMobileConnectUseCase,
                mudMobileDiscover = mudMobileDiscoverUseCase,
                warlockSettingsSync = warlockSettingsSync,
                connectToTelnet = connectToTelnetUseCase,
            )
        }

    /** The new-game wizard of the window showing [gameState]. */
    fun wizard(gameState: GameState): SgeViewModel =
        with(container) {
            SgeViewModel(
                gameState = gameState,
                settings = sgeSettings,
                clientSettingRepository = clientSettings,
                accountRepository = accountRepository,
                connectionRepository = connectionRepository,
                sgeClientFactory = sgeClientFactory,
                connectToGameUseCase = connectToGameUseCase,
                gameStarter = wizardStarter(gameState),
            )
        }

    /** Starts the wizard's games: a failed reconnect goes back to the wizard, as a failed connect does. */
    internal fun wizardStarter(gameState: GameState): GameStarter =
        container.gameViewModelFactory.starter(gameState, returnTo = GameScreen.NewGameState)
}
