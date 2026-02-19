package warlockfe.warlock3.compose.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.ConnectToGameUseCase
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.core.prefs.repositories.ConnectionRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionSettingsRepository
import warlockfe.warlock3.core.sge.AutoConnectResult
import warlockfe.warlock3.core.sge.ConnectionProxySettings
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SgeSettings
import warlockfe.warlock3.core.sge.StoredConnection

class DashboardViewModel(
    private val gameState: GameState,
    private val sgeSettings: SgeSettings,
    private val connectionRepository: ConnectionRepository,
    private val connectionSettingsRepository: ConnectionSettingsRepository,
    private val sgeClientFactory: SgeClientFactory,
    private val connectToGame: ConnectToGameUseCase,
) : ViewModel() {

    val connections = connectionRepository.observeAllConnections()

    private val logger = Logger.withTag("DashboardViewModel")

    var busy by mutableStateOf(false)
        private set

    var message: String? by mutableStateOf(null)
        private set

    private var connectJob: Job? = null

    fun connect(connection: StoredConnection) {
        if (busy) return
        busy = true
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            try {
                message = "Connecting..."
                val sgeClient = sgeClientFactory.create()
                val result = sgeClient.autoConnect(sgeSettings, connection)
                sgeClient.close()
                when (result) {
                    is AutoConnectResult.Failure ->
                        message = result.reason

                    is AutoConnectResult.Success ->
                        connectToGame(result.credentials, connection.proxySettings, gameState)
                }
            } catch (e: Exception) {
                ensureActive()
                logger.e(e) { "Error connecting to server" }
                gameState.setScreen(
                    GameScreen.ErrorState(
                        message = "Error: ${e.message}",
                        returnTo = GameScreen.Dashboard,
                    )
                )
            } finally {
                connectJob = null
                busy = false
            }
        }
    }

    fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
        message = null
    }

    fun updateProxySettings(characterId: String, proxySettings: ConnectionProxySettings) {
        viewModelScope.launch {
            connectionSettingsRepository.saveProxySettings(characterId, proxySettings)
        }
    }

    fun deleteConnection(id: String) {
        viewModelScope.launch {
            connectionRepository.deleteConnection(id)
        }
    }
}
