package warlockfe.warlock3.compose.ui.dashboard

import warlockfe.warlock3.compose.ConnectToGameUseCase
import warlockfe.warlock3.compose.MudMobileConnectUseCase
import warlockfe.warlock3.compose.MudMobileDiscoverUseCase
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.core.mudmobile.MudMobileApi
import warlockfe.warlock3.core.prefs.repositories.AccountRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionSettingsRepository
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SgeSettings

class DashboardViewModelFactory(
    private val connectionRepository: ConnectionRepository,
    private val connectionSettingsRepository: ConnectionSettingsRepository,
    private val sgeClientFactory: SgeClientFactory,
    private val connectToGameUseCase: ConnectToGameUseCase,
    private val clientSettingRepository: ClientSettingRepository,
    private val accountRepository: AccountRepository,
    private val mudMobileApi: MudMobileApi,
    private val mudMobileConnectUseCase: MudMobileConnectUseCase,
    private val mudMobileDiscoverUseCase: MudMobileDiscoverUseCase,
) {
    fun create(
        gameState: GameState,
        sgeSettings: SgeSettings,
    ): DashboardViewModel =
        DashboardViewModel(
            gameState = gameState,
            sgeSettings = sgeSettings,
            connectionRepository = connectionRepository,
            connectionSettingsRepository = connectionSettingsRepository,
            sgeClientFactory = sgeClientFactory,
            connectToGame = connectToGameUseCase,
            clientSettingRepository = clientSettingRepository,
            accountRepository = accountRepository,
            mudMobileApi = mudMobileApi,
            mudMobileConnect = mudMobileConnectUseCase,
            mudMobileDiscover = mudMobileDiscoverUseCase,
        )
}
