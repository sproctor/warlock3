package warlockfe.warlock3.compose

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.compose.ui.window.WindowRegistryFactory
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.sge.StoredConnection
import warlockfe.warlock3.core.window.WindowRegistry
import warlockfe.warlock3.telnet.network.TelnetClientFactory
import warlockfe.warlock3.telnet.network.TelnetSocket

sealed interface TelnetConnectResult {
    data object Success : TelnetConnectResult

    data class Failure(
        val message: String,
    ) : TelnetConnectResult
}

/**
 * Dials a telnet MUD and puts the game screen up. There is no login step of ours: the connection
 * says where to dial, and whatever the MUD asks on arrival (a name, a password) the user answers
 * in the game window.
 */
class ConnectToTelnetUseCase(
    private val windowRegistryFactory: WindowRegistryFactory,
    private val telnetClientFactory: TelnetClientFactory,
    private val gameViewModelFactory: GameViewModelFactory,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val logger = Logger.withTag("ConnectToTelnetUseCase")

    suspend operator fun invoke(
        connection: StoredConnection,
        gameState: GameState,
    ): TelnetConnectResult {
        val address =
            connection.telnetAddress
                ?: return TelnetConnectResult.Failure("${connection.name} has no host and port to connect to.")
        return withContext(ioDispatcher) {
            // Until they are handed off to the GameViewModel, the socket (and the client and window
            // registry, once there are any) are ours to close: if we're cancelled (the user gave up
            // waiting) or fail before that handoff, the finally closes them so nothing is leaked.
            var createdClient: WarlockClient? = null
            var createdRegistry: WindowRegistry? = null
            var handedOff = false
            val socket = TelnetSocket(ioDispatcher)
            try {
                socket.connect(address.host, address.port, address.tls)
                val windowRegistry = windowRegistryFactory.create().also { createdRegistry = it }
                val client =
                    telnetClientFactory.create(
                        windowRegistry = windowRegistry,
                        socket = socket,
                        gameCode = connection.code,
                        character = connection.character,
                    )
                createdClient = client
                client.connect("")
                val gameViewModel =
                    gameViewModelFactory.create(
                        client = client,
                        windowRegistry = windowRegistry,
                        reconnect = {
                            val result = invoke(connection, gameState)
                            if (result is TelnetConnectResult.Failure) {
                                gameState.setScreen(
                                    GameScreen.ErrorState(result.message, returnTo = GameScreen.Dashboard),
                                )
                            }
                        },
                    )
                gameState.setScreen(GameScreen.ConnectedGameState(gameViewModel))
                // Ownership has passed to the GameViewModel; don't tear it down in finally.
                handedOff = true
                TelnetConnectResult.Success
            } catch (e: Exception) {
                ensureActive()
                logger.e(e) { "Failed to connect to ${address.host}:${address.port}" }
                TelnetConnectResult.Failure(
                    "Could not connect to ${address.host}:${address.port}: ${e.message ?: e::class.simpleName}",
                )
            } finally {
                if (!handedOff) {
                    createdClient?.close() ?: socket.close()
                    createdRegistry?.close()
                }
            }
        }
    }
}
