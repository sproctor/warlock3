package warlockfe.warlock3.compose

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import warlockfe.warlock3.compose.ui.window.WindowRegistryFactory
import warlockfe.warlock3.core.sge.StoredConnection
import warlockfe.warlock3.telnet.network.TelnetClientFactory
import warlockfe.warlock3.telnet.network.TelnetSocket

/**
 * Dials a telnet MUD and hands it to a [GameStarter]. There is no login step of ours: the
 * connection says where to dial, and whatever the MUD asks on arrival (a name, a password) the
 * user answers in the game window.
 */
class ConnectToTelnetUseCase(
    private val windowRegistryFactory: WindowRegistryFactory,
    private val telnetClientFactory: TelnetClientFactory,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(
        connection: StoredConnection,
        starter: GameStarter,
    ): GameConnectResult {
        val address =
            connection.telnetAddress
                ?: return GameConnectResult.Failure("${connection.name} has no host and port to connect to.")
        return withContext(ioDispatcher) {
            val socket = TelnetSocket(ioDispatcher)
            dialAndStart(
                windowRegistryFactory = windowRegistryFactory,
                dial = { socket.connect(address.host, address.port, address.tls) },
                closeSocket = { if (!socket.isClosed) socket.close() },
                createClient = { windowRegistry ->
                    telnetClientFactory.create(
                        windowRegistry = windowRegistry,
                        socket = socket,
                        gameCode = connection.code,
                        character = connection.character,
                        acceptScripts = connection.acceptScripts,
                    )
                },
                key = "",
                reconnect = { invoke(connection, starter) },
                starter = starter,
            ).toResult("${address.host}:${address.port}")
        }
    }
}
