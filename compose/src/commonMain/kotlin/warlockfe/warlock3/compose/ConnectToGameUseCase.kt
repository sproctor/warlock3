package warlockfe.warlock3.compose

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
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

/**
 * Dials a Simutronics game server with the key from a login (or the command line), or plays a game
 * read from a stream, and hands what it dialed to a [GameStarter].
 */
class ConnectToGameUseCase(
    private val warlockProxyFactory: WarlockProxy.Factory,
    private val windowRegistryFactory: WindowRegistryFactory,
    private val warlockClientFactory: WarlockClientFactory,
    private val dirs: WarlockDirs,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val logger = Logger.withTag("ConnectToGameUseCase")

    /**
     * Dials the game at [credentials] (through the proxy [proxySettings] describe, if it is enabled)
     * and hands it to [starter]. With a proxy that is still starting up, keeps trying for a while.
     */
    suspend operator fun invoke(
        credentials: SimuGameCredentials,
        proxySettings: ConnectionProxySettings,
        starter: GameStarter,
    ): GameConnectResult =
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
            var result: GameConnectResult? = null
            while (result == null) {
                val socket = NetworkSocket(ioDispatcher)
                result =
                    try {
                        startGame(
                            socket = socket,
                            key = loginCredentials.key,
                            starter = starter,
                            // Reuse the same credentials (and key) on reconnect.
                            reconnect = { invoke(credentials, proxySettings, starter) },
                            connectSocket = { socket.connect(loginCredentials.host, loginCredentials.port) },
                            configureClient = { client -> proxy?.let { (client as WraythClient).setProxy(it) } },
                        )
                        GameConnectResult.Started
                    } catch (e: Exception) {
                        ensureActive()
                        val message = "Error connecting to ${loginCredentials.host}:${loginCredentials.port}"
                        logger.d(e) { message }
                        when {
                            proxy == null -> {
                                GameConnectResult.Failure(message)
                            }

                            !proxy.isAlive -> {
                                GameConnectResult.Failure("Proxy process terminated before connecting")
                            }

                            deadline.hasPassedNow() -> {
                                proxy.close()
                                GameConnectResult.Failure("Timed out waiting for proxy to accept connection")
                            }

                            // The proxy is still starting up: try again shortly.
                            else -> {
                                delay(500.milliseconds)
                                null
                            }
                        }
                    }
            }
            result
        }

    /**
     * Plays a game read from the socket [open] returns (a log being replayed, or stdin) instead of
     * from a server, handing it to [starter]. There is no key to send and nothing to reconnect to.
     * [open] runs on the IO dispatcher, since opening a file blocks.
     */
    suspend fun connectStream(
        open: () -> WarlockSocket,
        starter: GameStarter,
    ): GameConnectResult =
        withContext(ioDispatcher) {
            try {
                startGame(socket = open(), key = "", starter = starter)
                GameConnectResult.Started
            } catch (e: Exception) {
                ensureActive()
                logger.e(e) { "Failed to play the stream" }
                GameConnectResult.Failure("Error: ${e.message}")
            }
        }

    /**
     * Connects [socket] (through [connectSocket]), builds a client on it and hands both to [starter].
     *
     * The socket, and the window registry made here, are ours to close until that handoff: whatever
     * fails before it closes them, so a failed attempt leaves nothing behind. From the handoff on,
     * the starter owns them.
     */
    private suspend fun startGame(
        socket: WarlockSocket,
        key: String,
        starter: GameStarter,
        reconnect: (suspend () -> GameConnectResult)? = null,
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
            starter.start(GameConnection(client, windowRegistry, key, reconnect))
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
