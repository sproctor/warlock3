package warlockfe.warlock3.compose

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import warlockfe.warlock3.compose.ui.window.WindowRegistryFactory
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.sge.ConnectionProxySettings
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.util.WarlockDirs
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
            val target: SimuGameCredentials
            val proxy: WarlockProxy?
            if (proxySettings.enabled) {
                val substitutions =
                    mapOf(
                        "{home}" to dirs.homeDir,
                        "{host}" to credentials.host,
                        "{port}" to credentials.port.toString(),
                    )
                val proxyCommand = proxySettings.launchCommand?.substitute(substitutions)
                target =
                    credentials.copy(
                        host = proxySettings.host?.substitute(substitutions) ?: "localhost",
                        port = proxySettings.port?.substitute(substitutions)?.toIntOrNull() ?: credentials.port,
                    )
                proxy =
                    proxyCommand?.let {
                        logger.d { "Launching proxy command: $it" }
                        warlockProxyFactory.create(it)
                    }
            } else {
                target = credentials
                proxy = null
            }
            val address = "${target.host}:${target.port}"
            val deadline = TimeSource.Monotonic.markNow() + 30.seconds
            var result: GameConnectResult? = null
            try {
                while (result == null) {
                    val socket = NetworkSocket(ioDispatcher)
                    val attempt =
                        dialAndStart(
                            windowRegistryFactory = windowRegistryFactory,
                            dial = { socket.connect(target.host, target.port) },
                            closeSocket = { if (!socket.isClosed) socket.close() },
                            createClient = { windowRegistry ->
                                warlockClientFactory
                                    .createClient(windowRegistry = windowRegistry, socket = socket)
                                    .also { client -> proxy?.let { (client as WraythClient).setProxy(it) } }
                            },
                            key = target.key,
                            // Reuse the same credentials (and key) on reconnect. That launches a new
                            // proxy, so this one goes first: it would hold the port the new one wants,
                            // and the old client it belongs to closes it soon anyway.
                            reconnect = {
                                proxy?.close()
                                invoke(credentials, proxySettings, starter)
                            },
                            starter = starter,
                        )
                    result =
                        when {
                            // Only a proxy still starting up is worth another try, and only one that
                            // refused us: one that let us in is up.
                            attempt !is StartAttempt.DialFailed || proxy == null -> {
                                attempt.toResult(address)
                            }

                            !proxy.isAlive -> {
                                GameConnectResult.Failure("Proxy process terminated before connecting")
                            }

                            deadline.hasPassedNow() -> {
                                GameConnectResult.Failure("Timed out waiting for proxy to accept connection")
                            }

                            else -> {
                                delay(500.milliseconds)
                                null
                            }
                        }
                }
                result
            } finally {
                // A started game's client owns the proxy, and closes it with itself. Otherwise (a
                // failure, or the user gave up waiting) nothing else ever will.
                if (result != GameConnectResult.Started) proxy?.close()
            }
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
            var socket: WarlockSocket? = null
            val attempt =
                dialAndStart(
                    windowRegistryFactory = windowRegistryFactory,
                    dial = { socket = open() },
                    closeSocket = { socket?.let { if (!it.isClosed) it.close() } },
                    createClient = { windowRegistry ->
                        warlockClientFactory.createClient(windowRegistry = windowRegistry, socket = checkNotNull(socket))
                    },
                    key = "",
                    reconnect = null,
                    starter = starter,
                )
            when (attempt) {
                StartAttempt.Started -> GameConnectResult.Started
                is StartAttempt.DialFailed -> GameConnectResult.Failure("Could not open the stream: ${attempt.cause.reason}")
                is StartAttempt.StartFailed -> GameConnectResult.Failure("The stream didn't play: ${attempt.cause.reason}")
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
