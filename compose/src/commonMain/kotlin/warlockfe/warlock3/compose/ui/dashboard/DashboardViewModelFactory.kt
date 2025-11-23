package warlockfe.warlock3.compose.ui.dashboard

import warlockfe.warlock3.compose.ConnectToGameUseCase
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.core.prefs.repositories.ConnectionRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionSettingsRepository
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SgeSettings

class DashboardViewModelFactory(
    private val connectionRepository: ConnectionRepository,
    private val connectionSettingsRepository: ConnectionSettingsRepository,
    private val sgeClientFactory: SgeClientFactory,
    private val connectToGameUseCase: ConnectToGameUseCase,
) {
    fun create(
        gameState: GameState,
        sgeSettings: SgeSettings,
    ): DashboardViewModel {
        return DashboardViewModel(
            gameState = gameState,
            sgeSettings = sgeSettings,
            connectionRepository = connectionRepository,
            connectionSettingsRepository = connectionSettingsRepository,
            sgeClientFactory = sgeClientFactory,
            connectToGame = connectToGameUseCase,
        )
    }
}
