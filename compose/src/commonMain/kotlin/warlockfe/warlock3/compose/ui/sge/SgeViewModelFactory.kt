package warlockfe.warlock3.compose.ui.sge

import kotlinx.coroutines.CoroutineDispatcher
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.compose.ui.window.WindowRegistryFactory
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.prefs.repositories.AccountRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionRepository
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SgeSettings

class SgeViewModelFactory(
    private val clientSettingRepository: ClientSettingRepository,
    private val accountRepository: AccountRepository,
    private val connectionRepository: ConnectionRepository,
    private val warlockClientFactory: WarlockClientFactory,
    private val sgeClientFactory: SgeClientFactory,
    private val gameViewModelFactory: GameViewModelFactory,
    private val windowRegistryFactory: WindowRegistryFactory,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun create(
        gameState: GameState,
        settings: SgeSettings,
    ): SgeViewModel {
        return SgeViewModel(
            gameState = gameState,
            settings = settings,
            clientSettingRepository = clientSettingRepository,
            accountRepository = accountRepository,
            connectionRepository = connectionRepository,
            warlockClientFactory = warlockClientFactory,
            sgeClientFactory = sgeClientFactory,
            gameViewModelFactory = gameViewModelFactory,
            windowRegistryFactory = windowRegistryFactory,
            ioDispatcher = ioDispatcher,
        )
    }
}
