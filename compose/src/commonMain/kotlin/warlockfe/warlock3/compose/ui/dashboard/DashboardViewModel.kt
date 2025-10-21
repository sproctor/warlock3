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
import kotlinx.coroutines.withContext
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.compose.ui.window.StreamRegistryFactory
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.client.WarlockSocketFactory
import warlockfe.warlock3.core.prefs.repositories.ConnectionRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.WindowRepositoryFactory
import warlockfe.warlock3.core.sge.ConnectionProxySettings
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SgeEvent
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.sge.StoredConnection
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.wrayth.network.WraythClient
import java.io.IOException
import java.net.UnknownHostException

class DashboardViewModel(
    private val gameState: GameState,
    private val sgeHost: String,
    private val sgePort: Int,
    private val simuCert: ByteArray,
    private val connectionRepository: ConnectionRepository,
    private val connectionSettingsRepository: ConnectionSettingsRepository,
    private val sgeClientFactory: SgeClientFactory,
    private val warlockClientFactory: WarlockClientFactory,
    private val gameViewModelFactory: GameViewModelFactory,
    private val windowRepositoryFactory: WindowRepositoryFactory,
    private val streamRegistryFactory: StreamRegistryFactory,
    private val warlockSocketFactory: WarlockSocketFactory,
    private val warlockProxyFactory: WarlockProxy.Factory,
    private val dirs: WarlockDirs,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val connections = connectionRepository.observeAllConnections()

    private val logger = KotlinLogging.logger { }

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
                if (!sgeClient.connect(host = sgeHost, port = sgePort, certificate = simuCert)) {
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
                                    gameState.setScreen(
                                        GameScreen.ErrorState(
                                            "Unknown host: ${e.message}",
                                            returnTo = GameScreen.Dashboard
                                        )
                                    )
                                } catch (e: IOException) {
                                    logger.error(e) { "Error connecting to server" }
                                    gameState.setScreen(
                                        GameScreen.ErrorState("Error: ${e.message}", returnTo = GameScreen.Dashboard)
                                    )
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
            } catch (_: IOException) {
                // Do we care?
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
        withContext(ioDispatcher) {
            var loginCredentials = credentials
            var proxy: WarlockProxy? = null
            if (proxySettings.enabled) {
                val substitutions = mapOf(
                    "{home}" to dirs.homeDir,
                    "{host}" to loginCredentials.host,
                    "{port}" to loginCredentials.port.toString(),
                )
                val proxyCommand = proxySettings.launchCommand?.substitute(substitutions)
                val proxyHost = proxySettings.host?.substitute(substitutions) ?: "localhost"
                val proxyPort = proxySettings.port?.substitute(substitutions)?.toIntOrNull() ?: loginCredentials.port
                loginCredentials = credentials.copy(
                    host = proxyHost,
                    port = proxyPort,
                )
                if (proxyCommand != null) {
                    logger.debug { "Launching proxy command: $proxyCommand" }
                    proxy = warlockProxyFactory.create(proxyCommand)
                }
            }
            val windowRepository = windowRepositoryFactory.create()
            val streamRegistry = streamRegistryFactory.create(windowRepository)
            while (true) {
                try {
                    val socket = warlockSocketFactory.create(loginCredentials.host, loginCredentials.port)
                    val sfClient = warlockClientFactory.createClient(
                        windowRepository = windowRepository,
                        streamRegistry = streamRegistry,
                        socket = socket,
                    ) as WraythClient
                    proxy?.let { sfClient.setProxy(it) }
                    sfClient.connect(loginCredentials.key)
                    val gameViewModel = gameViewModelFactory.create(
                        client = sfClient,
                        windowRepository = windowRepository,
                        streamRegistry = streamRegistry,
                    )
                    gameState.setScreen(GameScreen.ConnectedGameState(gameViewModel))
                    break
                } catch (e: UnknownHostException) {
                    logger.debug { "Unknown host" }
                    gameState.setScreen(GameScreen.ErrorState(e.message ?: "Uknown host", returnTo = GameScreen.Dashboard))
                    break
                } catch (e: IOException) {
                    val message = "Error connecting to ${loginCredentials.host}:$loginCredentials.port"
                    logger.debug(e) { message }
                    if (proxy == null) {
                        gameState.setScreen(GameScreen.ErrorState(message, returnTo = GameScreen.Dashboard))
                        break
                    }
                    if (!proxy.isAlive) {
                        gameState.setScreen(GameScreen.ErrorState("Proxy process terminated before connecting", returnTo = GameScreen.Dashboard))
                        break
                    }
                    delay(500L)
                }
            }
            // TODO: test if proxy is dead, and throw an error if we never connected
        }
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
