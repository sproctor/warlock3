package warlockfe.warlock3.compose.ui.dashboard

import kotlinx.coroutines.CoroutineDispatcher
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.compose.ui.window.StreamRegistryFactory
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.client.WarlockSocketFactory
import warlockfe.warlock3.core.prefs.repositories.ConnectionRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.WindowRepositoryFactory
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.util.WarlockDirs

class DashboardViewModelFactory(
    private val connectionRepository: ConnectionRepository,
    private val connectionSettingsRepository: ConnectionSettingsRepository,
    private val gameViewModelFactory: GameViewModelFactory,
    private val sgeClientFactory: SgeClientFactory,
    private val warlockClientFactory: WarlockClientFactory,
    private val windowRepositoryFactory: WindowRepositoryFactory,
    private val streamRegistryFactory: StreamRegistryFactory,
    private val warlockSocketFactory: WarlockSocketFactory,
    private val warlockProxyFactory: WarlockProxy.Factory,
    private val dirs: WarlockDirs,
    private val ioDispatcher: CoroutineDispatcher,
    private val simuCert: ByteArray,
) {
    fun create(
        gameState: GameState,
        sgeHost: String,
        sgePort: Int,
    ): DashboardViewModel {
        return DashboardViewModel(
            gameState = gameState,
            sgeHost = sgeHost,
            sgePort = sgePort,
            connectionRepository = connectionRepository,
            connectionSettingsRepository = connectionSettingsRepository,
            gameViewModelFactory = gameViewModelFactory,
            sgeClientFactory = sgeClientFactory,
            warlockClientFactory = warlockClientFactory,
            windowRepositoryFactory = windowRepositoryFactory,
            streamRegistryFactory = streamRegistryFactory,
            dirs = dirs,
            ioDispatcher = ioDispatcher,
            warlockSocketFactory = warlockSocketFactory,
            warlockProxyFactory = warlockProxyFactory,
            simuCert = simuCert,
        )
    }
}
