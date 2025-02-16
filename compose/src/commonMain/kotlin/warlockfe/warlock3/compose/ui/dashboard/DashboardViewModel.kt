package warlockfe.warlock3.compose.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.prefs.ConnectionRepository
import warlockfe.warlock3.core.prefs.ConnectionSettingsRepository
import warlockfe.warlock3.core.sge.ConnectionProxySettings
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SgeEvent
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.sge.StoredConnection
import warlockfe.warlock3.stormfront.network.StormfrontClient
import java.io.IOException
import java.net.UnknownHostException

class DashboardViewModel(
    private val connectionRepository: ConnectionRepository,
    private val connectionSettingsRepository: ConnectionSettingsRepository,
    private val gameState: GameState,
    private val sgeClientFactory: SgeClientFactory,
    private val warlockClientFactory: WarlockClientFactory,
    private val gameViewModelFactory: GameViewModelFactory,
) : ViewModel() {

    val connections = connectionRepository.observeAllConnections()

    private val logger = KotlinLogging.logger { }

    var busy by mutableStateOf(false)
        private set

    var message: String? by mutableStateOf(null)
        private set

    // TODO: Make these configurable?
    private val host = "eaccess.play.net"
    private val port = 7900

    private var connectJob: Job? = null

    fun connect(connection: StoredConnection) {
        if (busy) return
        busy = true
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            try {
                message = "Connecting..."
                val sgeClient = sgeClientFactory.create(host = host, port = port)
                if (!sgeClient.connect()) {
                    logger.error { "Unable to connect to server" }
                    message = "Could not connect to SGE"
                    return@launch
                }
                sgeClient.login(username = connection.username, password = connection.password ?: "")

                sgeClient.eventFlow
                    .collect { event ->
                        when (event) {
                            is SgeEvent.SgeLoginSucceededEvent -> sgeClient.selectGame(connection.code)
                            is SgeEvent.SgeGameSelectedEvent -> sgeClient.requestCharacterList()
                            is SgeEvent.SgeCharactersReadyEvent -> {
                                val characters = event.characters
                                val sgeCharacter = characters.firstOrNull { it.name.equals(connection.character, true) }
                                if (sgeCharacter == null) {
                                    message = "Could not find character: ${connection.character}"
                                } else {
                                    sgeClient.selectCharacter(sgeCharacter.code)
                                }
                            }

                            is SgeEvent.SgeReadyToPlayEvent -> {
                                try {
                                    connectToGame(event.credentials, connection.proxySettings)
                                } catch (e: UnknownHostException) {
                                    gameState.screen = GameScreen.ErrorState(
                                        "Unknown host: ${e.message}",
                                        returnTo = GameScreen.Dashboard
                                    )
                                } catch (e: Exception) {
                                    logger.error(e) { "Error connecting to server" }
                                    gameState.screen =
                                        GameScreen.ErrorState("Error: ${e.message}", returnTo = GameScreen.Dashboard)
                                }
                                sgeClient.close()
                                cancelConnect()
                            }

                            is SgeEvent.SgeErrorEvent -> {
                                message = "Error code (${event.errorCode})"
                                connectJob?.cancel()
                            }

                            else -> Unit // we don't care?
                        }
                    }
            } catch (e: IOException) {

            } finally {
                connectJob = null
                busy = false
            }
        }
    }

    fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
    }

    fun updateProxySettings(characterId: String, proxySettings: ConnectionProxySettings) {
        viewModelScope.launch {
            connectionSettingsRepository.saveProxySettings(characterId, proxySettings)
        }
    }

    private suspend fun connectToGame(credentials: SimuGameCredentials, proxySettings: ConnectionProxySettings) {
        var loginCredentials = credentials
        var process: Process? = null
        if (proxySettings.enabled) {
            val substitutions = mapOf(
                "{host}" to loginCredentials.host,
                "{port}" to loginCredentials.port.toString(),
            )
            val proxyCommand = proxySettings.launchCommand?.substitute(substitutions)
            val proxyHost = proxySettings.host?.substitute(substitutions) ?: "localhost"
            val proxyPort = proxySettings.port?.substitute(substitutions)?.toInt() ?: loginCredentials.port
            loginCredentials = credentials.copy(
                host = proxyHost,
                port = proxyPort,
            )
            if (proxyCommand != null) {
                logger.debug { "Launching proxy command: $proxyCommand" }
                process = Runtime.getRuntime().exec(proxyCommand)
            }
        }
        val sfClient = warlockClientFactory.createStormFrontClient(
            credentials = loginCredentials,
            windowRepository = gameState.windowRepository,
            streamRegistry = gameState.streamRegistry,
        ) as StormfrontClient
        process?.let { sfClient.setProxy(it) }
        do {
            try {
                sfClient.connect()
                break
            } catch (_: UnknownHostException) {
                logger.debug { "Unknown host" }
                break
            } catch (e: IOException) {
                logger.debug(e) { "Error connecting to $host:$port" }
                delay(500L)
            }
        } while (process != null && process.isAlive)
        val gameViewModel = gameViewModelFactory.create(
            sfClient,
            gameState.windowRepository,
            gameState.streamRegistry,
        )
        gameState.screen = GameScreen.ConnectedGameState(gameViewModel)
    }

    fun deleteConnection(id: String) {
        viewModelScope.launch {
            connectionRepository.deleteConnection(id)
        }
    }
}

private fun String.substitute(substitutions: Map<String, String>): String {
    var result = this
    substitutions.forEach { (key, value) ->
        result = result.replace(key, value)
    }
    return result
}
