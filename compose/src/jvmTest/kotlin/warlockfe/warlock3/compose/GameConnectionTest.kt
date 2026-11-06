package warlockfe.warlock3.compose

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.core.sge.ConnectionProtocol
import warlockfe.warlock3.core.sge.ConnectionProxySettings
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.sge.StoredConnection
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The connect use cases dial and hand what they dialed to a [GameStarter]; they know nothing of
 * screens. Putting the game on screen, and reporting a reconnect that fails, is the game view model
 * factory's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameConnectionTest {
    private lateinit var dir: java.nio.file.Path
    private var container: AppContainer? = null

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("game-connection-test")
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        container?.database?.close()
        dir.deleteRecursively()
    }

    /** Records what it is handed, and closes it: nothing here needs the game to run. */
    private class RecordingStarter : GameStarter {
        val started = mutableListOf<GameConnection>()

        override suspend fun start(connection: GameConnection) {
            started += connection
            connection.client.close()
            connection.windowRegistry.close()
        }
    }

    @Test
    fun aGameServerIsDialedAndHandedToTheStarterAndSoIsItsReconnect() =
        runBlocking {
            val appContainer = testAppContainer(dir, Dispatchers.IO).also { container = it }
            ServerSocket(0).use { server ->
                val credentials = SimuGameCredentials(host = "127.0.0.1", port = server.localPort, key = "KEY")
                val starter = RecordingStarter()

                val result = appContainer.connectToGameUseCase(credentials, ConnectionProxySettings.NONE, starter)

                assertEquals(GameConnectResult.Started, result)
                val connection = starter.started.single()
                assertEquals("KEY", connection.key)
                // A reconnect dials the same server with the same key, and starts it the same way.
                assertEquals(GameConnectResult.Started, assertNotNull(connection.reconnect).invoke())
                assertEquals(listOf("KEY", "KEY"), starter.started.map { it.key })
            }
        }

    @Test
    fun aServerThatRefusesIsAFailureAndNothingIsStarted() =
        runBlocking {
            val appContainer = testAppContainer(dir, Dispatchers.IO).also { container = it }
            val closedPort = ServerSocket(0).use { it.localPort }
            val starter = RecordingStarter()

            val game =
                appContainer.connectToGameUseCase(
                    SimuGameCredentials(host = "127.0.0.1", port = closedPort, key = "KEY"),
                    ConnectionProxySettings.NONE,
                    starter,
                )
            val telnet =
                appContainer.connectToTelnetUseCase(
                    StoredConnection(
                        id = "mud",
                        name = "Mud",
                        username = "",
                        password = null,
                        character = "",
                        code = "",
                        proxySettings = ConnectionProxySettings.NONE,
                        protocol = ConnectionProtocol.TELNET,
                        host = "127.0.0.1",
                        port = closedPort,
                    ),
                    starter,
                )

            assertIs<GameConnectResult.Failure>(game)
            assertIs<GameConnectResult.Failure>(telnet)
            assertTrue(starter.started.isEmpty())
        }

    @Test
    fun aReconnectThatFailsPutsItsReasonOnScreen() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val appContainer = testAppContainer(dir, StandardTestDispatcher(testScheduler)).also { container = it }
            val windowRegistry = appContainer.windowRegistryFactory.create()
            val client =
                appContainer.warlockClientFactory.createClient(
                    windowRegistry = windowRegistry,
                    socket = ReplaySocket(listOf("Welcome back.")),
                )
            val gameState = GameState(startupConnection = null)

            appContainer.gameViewModelFactory.start(
                connection =
                    GameConnection(
                        client = client,
                        windowRegistry = windowRegistry,
                        key = "",
                        reconnect = { GameConnectResult.Failure("The server is gone.") },
                    ),
                gameState = gameState,
            )
            advanceUntilIdle()
            assertIs<GameScreen.ConnectedGameState>(gameState.screen).viewModel.reconnect()
            advanceUntilIdle()

            assertEquals("The server is gone.", assertIs<GameScreen.ErrorState>(gameState.screen).message)
        }
}
