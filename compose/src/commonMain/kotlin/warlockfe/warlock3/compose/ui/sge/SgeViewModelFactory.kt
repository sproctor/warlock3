package warlockfe.warlock3.compose.ui.sge

import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.prefs.AccountRepository
import warlockfe.warlock3.core.prefs.ClientSettingRepository
import warlockfe.warlock3.core.prefs.ConnectionRepository
import warlockfe.warlock3.core.sge.SgeClientFactory

class SgeViewModelFactory(
    private val clientSettingRepository: ClientSettingRepository,
    private val accountRepository: AccountRepository,
    private val connectionRepository: ConnectionRepository,
    private val warlockClientFactory: WarlockClientFactory,
    private val sgeClientFactory: SgeClientFactory,
    private val gameViewModelFactory: GameViewModelFactory,
) {
    fun create(
        gameState: GameState,
    ): SgeViewModel {
        return SgeViewModel(
            clientSettingRepository = clientSettingRepository,
            accountRepository = accountRepository,
            connectionRepository = connectionRepository,
            warlockClientFactory = warlockClientFactory,
            sgeClientFactory = sgeClientFactory,
            gameViewModelFactory = gameViewModelFactory,
            gameState = gameState,
        )
    }
}
