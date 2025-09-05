package warlockfe.warlock3.compose.ui.sge

import kotlinx.coroutines.CoroutineDispatcher
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.compose.ui.window.StreamRegistryFactory
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.prefs.repositories.AccountRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionRepository
import warlockfe.warlock3.core.prefs.repositories.WindowRepositoryFactory
import warlockfe.warlock3.core.sge.SgeClientFactory

class SgeViewModelFactory(
    private val clientSettingRepository: ClientSettingRepository,
    private val accountRepository: AccountRepository,
    private val connectionRepository: ConnectionRepository,
    private val warlockClientFactory: WarlockClientFactory,
    private val sgeClientFactory: SgeClientFactory,
    private val gameViewModelFactory: GameViewModelFactory,
    private val windowRepositoryFactory: WindowRepositoryFactory,
    private val streamRegistryFactory: StreamRegistryFactory,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun create(
        gameState: GameState,
    ): SgeViewModel {
        return SgeViewModel(
            gameState = gameState,
            clientSettingRepository = clientSettingRepository,
            accountRepository = accountRepository,
            connectionRepository = connectionRepository,
            warlockClientFactory = warlockClientFactory,
            sgeClientFactory = sgeClientFactory,
            gameViewModelFactory = gameViewModelFactory,
            windowRepositoryFactory = windowRepositoryFactory,
            streamRegistryFactory = streamRegistryFactory,
            ioDispatcher = ioDispatcher,
        )
    }
}
