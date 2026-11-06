package warlockfe.warlock3.compose

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.compose.ui.window.WindowRegistryFactory
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.sge.ConnectionProxySettings
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.core.window.WindowRegistry
import warlockfe.warlock3.wrayth.network.NetworkSocket
import warlockfe.warlock3.wrayth.network.WraythClient
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class ConnectToGameUseCase(
    private val warlockProxyFactory: WarlockProxy.Factory,
    private val windowRegistryFactory: WindowRegistryFactory,
    private val warlockClientFactory: WarlockClientFactory,
    private val gameViewModelFactory: GameViewModelFactory,
    private val dirs: WarlockDirs,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val logger = Logger.withTag("ConnectToGameUseCase")

    suspend operator fun invoke(
        credentials: SimuGameCredentials,
        proxySettings: ConnectionProxySettings,
        gameState: GameState,
    ) {
        withContext(ioDispatcher) {
            var loginCredentials = credentials
            var proxy: WarlockProxy? = null
            if (proxySettings.enabled) {
                val substitutions =
                    mapOf(
                        "{home}" to dirs.homeDir,
                        "{host}" to loginCredentials.host,
                        "{port}" to loginCredentials.port.toString(),
                    )
                val proxyCommand = proxySettings.launchCommand?.substitute(substitutions)
                val proxyHost = proxySettings.host?.substitute(substitutions) ?: "localhost"
                val proxyPort = proxySettings.port?.substitute(substitutions)?.toIntOrNull() ?: loginCredentials.port
                loginCredentials =
                    credentials.copy(
                        host = proxyHost,
                        port = proxyPort,
                    )
                if (proxyCommand != null) {
                    logger.d { "Launching proxy command: $proxyCommand" }
                    proxy = warlockProxyFactory.create(proxyCommand)
                }
            }
            val deadline = TimeSource.Monotonic.markNow() + 30.seconds
            while (true) {
                val socket = NetworkSocket(ioDispatcher)
                try {
                    startGame(
                        socket = socket,
                        key = loginCredentials.key,
                        gameState = gameState,
                        // Reuse the same credentials (and key) on reconnect.
                        reconnect = { invoke(credentials, proxySettings, gameState) },
                        connectSocket = { socket.connect(loginCredentials.host, loginCredentials.port) },
                        configureClient = { client -> proxy?.let { (client as WraythClient).setProxy(it) } },
                    )
                    break
                } catch (e: Exception) {
                    ensureActive()
                    val message = "Error connecting to ${loginCredentials.host}:${loginCredentials.port}"
                    logger.d(e) { message }
                    if (proxy == null) {
                        gameState.setScreen(GameScreen.ErrorState(message, returnTo = GameScreen.Dashboard))
                        break
                    }
                    if (!proxy.isAlive) {
                        gameState.setScreen(
                            GameScreen.ErrorState(
                                "Proxy process terminated before connecting",
                                returnTo = GameScreen.Dashboard,
                            ),
                        )
                        break
                    }
                    if (deadline.hasPassedNow()) {
                        proxy.close()
                        gameState.setScreen(
                            GameScreen.ErrorState(
                                "Timed out waiting for proxy to accept connection",
                                returnTo = GameScreen.Dashboard,
                            ),
                        )
                        break
                    }
                    delay(500.milliseconds)
                }
            }
        }
    }

    /**
     * Plays a game read from the socket [open] returns (a log being replayed, or stdin) instead of
     * from a server. There is no key to send and nothing to reconnect to. [open] runs on the IO
     * dispatcher, since opening a file blocks.
     */
    suspend fun connectStream(
        open: () -> WarlockSocket,
        gameState: GameState,
    ) {
        withContext(ioDispatcher) {
            startGame(socket = open(), key = "", gameState = gameState)
        }
    }

    /**
     * Connects [socket] (through [connectSocket]), builds a client on it and hands both to the game
     * view model factory, which puts the game on screen.
     *
     * The socket, and the window registry made here, are ours to close until that handoff: whatever
     * fails before it closes them, so a failed attempt leaves nothing behind. From the handoff on,
     * the factory owns them, and closes them itself if the game doesn't make it on screen.
     */
    private suspend fun startGame(
        socket: WarlockSocket,
        key: String,
        gameState: GameState,
        reconnect: (suspend () -> Unit)? = null,
        connectSocket: suspend () -> Unit = {},
        configureClient: (WarlockClient) -> Unit = {},
    ) {
        var registry: WindowRegistry? = null
        var handedOff = false
        try {
            connectSocket()
            // Created once the socket is up, so a refused attempt doesn't make one at all.
            val windowRegistry = windowRegistryFactory.create().also { registry = it }
            val client = warlockClientFactory.createClient(windowRegistry = windowRegistry, socket = socket)
            configureClient(client)
            handedOff = true
            gameViewModelFactory.connect(
                client = client,
                key = key,
                windowRegistry = windowRegistry,
                gameState = gameState,
                reconnect = reconnect,
            )
        } finally {
            if (!handedOff) {
                // A refused connect has already closed its socket.
                if (!socket.isClosed) socket.close()
                registry?.close()
            }
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
