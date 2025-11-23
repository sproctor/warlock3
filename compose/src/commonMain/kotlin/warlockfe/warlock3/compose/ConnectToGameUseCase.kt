package warlockfe.warlock3.compose

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.compose.ui.window.StreamRegistryFactory
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.prefs.repositories.WindowRepositoryFactory
import warlockfe.warlock3.core.sge.ConnectionProxySettings
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.wrayth.network.NetworkSocket
import warlockfe.warlock3.wrayth.network.WraythClient

// TODO: put this someplace more sensible
class ConnectToGameUseCase(
    private val warlockProxyFactory: WarlockProxy.Factory,
    private val windowRepositoryFactory: WindowRepositoryFactory,
    private val streamRegistryFactory: StreamRegistryFactory,
    private val warlockClientFactory: WarlockClientFactory,
    private val gameViewModelFactory: GameViewModelFactory,
    private val dirs: WarlockDirs,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val logger = KotlinLogging.logger {}
    suspend operator fun invoke(
        credentials: SimuGameCredentials,
        proxySettings: ConnectionProxySettings,
        gameState: GameState,
    ) {
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
                    val socket = NetworkSocket(ioDispatcher)
                    socket.connect(loginCredentials.host, loginCredentials.port)
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
                } catch (e: Exception) {
                    ensureActive()
                    val message = "Error connecting to ${loginCredentials.host}:$loginCredentials.port"
                    logger.debug(e) { message }
                    if (proxy == null) {
                        gameState.setScreen(GameScreen.ErrorState(message, returnTo = GameScreen.Dashboard))
                        break
                    }
                    if (!proxy.isAlive) {
                        gameState.setScreen(
                            GameScreen.ErrorState(
                                "Proxy process terminated before connecting",
                                returnTo = GameScreen.Dashboard
                            )
                        )
                        break
                    }
                    delay(500L)
                }
            }
            // TODO: test if proxy is dead, and throw an error if we never connected
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