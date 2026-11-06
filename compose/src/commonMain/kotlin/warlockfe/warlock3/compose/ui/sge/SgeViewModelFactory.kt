package warlockfe.warlock3.compose.ui.sge

import warlockfe.warlock3.compose.ConnectToGameUseCase
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.core.prefs.repositories.AccountRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionRepository
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SgeSettings

class SgeViewModelFactory(
    private val clientSettingRepository: ClientSettingRepository,
    private val accountRepository: AccountRepository,
    private val connectionRepository: ConnectionRepository,
    private val sgeClientFactory: SgeClientFactory,
    private val connectToGameUseCase: ConnectToGameUseCase,
    private val gameViewModelFactory: GameViewModelFactory,
) {
    fun create(
        gameState: GameState,
        settings: SgeSettings,
    ): SgeViewModel =
        SgeViewModel(
            gameState = gameState,
            settings = settings,
            clientSettingRepository = clientSettingRepository,
            accountRepository = accountRepository,
            connectionRepository = connectionRepository,
            sgeClientFactory = sgeClientFactory,
            connectToGameUseCase = connectToGameUseCase,
            // A failed reconnect goes back to the wizard, as a failed connect does.
            gameStarter = gameViewModelFactory.starter(gameState, returnTo = GameScreen.NewGameState),
        )
}
