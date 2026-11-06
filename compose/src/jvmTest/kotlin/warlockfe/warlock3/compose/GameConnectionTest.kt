package warlockfe.warlock3.compose

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.sge.ConnectionProtocol
import warlockfe.warlock3.core.sge.ConnectionProxySettings
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.sge.StoredConnection
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The connect use cases dial and hand what they dialed to a [GameStarter]; they know nothing of
 * screens. Putting the game on screen, and reporting a reconnect that fails, is the game view model
 * factory's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameConnectionTest {
    private lateinit var dir: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("game-connection-test")
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        // The database stays open: a game's window registry queries it from flows that closing the
        // registry cancels but doesn't wait for, and a query still running when the database closes
        // crashes the JVM in native SQLite. Its files can go with it; the open handles don't mind.
        deleteTestDir(dir)
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
            val appContainer = testAppContainer(dir, Dispatchers.IO)
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
            val appContainer = testAppContainer(dir, Dispatchers.IO)
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

    /** A proxy that is alive until closed, and launches nothing. */
    private class FakeProxy : WarlockProxy {
        @Volatile
        var closed = false

        override val isAlive: Boolean get() = !closed
        override val stdOut: Flow<String> = emptyFlow()
        override val stdErr: Flow<String> = emptyFlow()

        override fun close() {
            closed = true
        }
    }

    /** Hands out [FakeProxy]s, remembering each, and whether every earlier one was closed first. */
    private class FakeProxyFactory : WarlockProxy.Factory {
        val proxies = CopyOnWriteArrayList<FakeProxy>()
        val launchedAfterEarlierClosed = CopyOnWriteArrayList<Boolean>()

        override fun create(command: String): WarlockProxy {
            launchedAfterEarlierClosed += proxies.all { it.closed }
            return FakeProxy().also { proxies += it }
        }
    }

    private fun proxyTo(port: Int) =
        ConnectionProxySettings(enabled = true, launchCommand = "proxy", host = "127.0.0.1", port = port.toString())

    @Test
    fun aProxyStillStartingUpIsTriedAgainAndAReconnectShutsTheOldOneFirst() =
        runBlocking {
            val proxyFactory = FakeProxyFactory()
            val appContainer = testAppContainer(dir, Dispatchers.IO, proxyFactory)
            val port = ServerSocket(0).use { it.localPort }
            val started = CopyOnWriteArrayList<GameConnection>()
            // Keeps what it is handed open, as a game on screen would.
            val starter = GameStarter { started += it }
            // The proxy starts listening a while after it is launched.
            val server =
                async(Dispatchers.IO) {
                    delay(1500)
                    ServerSocket(port)
                }
            try {
                val credentials = SimuGameCredentials(host = "game.example", port = 1, key = "KEY")
                val result = appContainer.connectToGameUseCase(credentials, proxyTo(port), starter)

                assertEquals(GameConnectResult.Started, result)
                val proxy = proxyFactory.proxies.single()
                // The game's client owns the proxy now.
                assertTrue(!proxy.closed)

                assertEquals(GameConnectResult.Started, assertNotNull(started.single().reconnect).invoke())
                assertEquals(listOf(true, true), proxyFactory.launchedAfterEarlierClosed)
                assertTrue(proxy.closed)
            } finally {
                started.forEach {
                    it.client.close()
                    it.windowRegistry.close()
                }
                server.await().close()
            }
        }

    @Test
    fun givingUpOnAProxyThatNeverListensShutsItDown() =
        runBlocking {
            val proxyFactory = FakeProxyFactory()
            val appContainer = testAppContainer(dir, Dispatchers.IO, proxyFactory)
            val closedPort = ServerSocket(0).use { it.localPort }
            val starter = RecordingStarter()

            val connect =
                launch(Dispatchers.IO) {
                    appContainer.connectToGameUseCase(
                        SimuGameCredentials(host = "game.example", port = 1, key = "KEY"),
                        proxyTo(closedPort),
                        starter,
                    )
                }
            withTimeout(10.seconds) { while (proxyFactory.proxies.isEmpty()) delay(20) }
            delay(1200)
            connect.cancelAndJoin()

            assertTrue(proxyFactory.proxies.single().closed, "the proxy outlived the connect")
            assertTrue(starter.started.isEmpty())
        }

    @Test
    fun aGameThatFailsToStartIsNotADialFailureAndIsNotRetried() =
        runBlocking {
            val proxyFactory = FakeProxyFactory()
            val appContainer = testAppContainer(dir, Dispatchers.IO, proxyFactory)
            ServerSocket(0).use { server ->
                var attempts = 0
                val starter =
                    GameStarter { connection ->
                        attempts++
                        // The starter owns what it was handed, failing or not.
                        connection.client.close()
                        connection.windowRegistry.close()
                        throw IllegalStateException("no window for it")
                    }

                val result =
                    appContainer.connectToGameUseCase(
                        SimuGameCredentials(host = "game.example", port = 1, key = "KEY"),
                        proxyTo(server.localPort),
                        starter,
                    )

                val message = assertIs<GameConnectResult.Failure>(result).message
                assertTrue("didn't start" in message && "no window for it" in message, message)
                assertEquals(1, attempts)
                assertTrue(proxyFactory.proxies.single().closed)
            }
        }

    @Test
    fun aReconnectThatFailsPutsItsReasonOnScreen() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val appContainer = testAppContainer(dir, StandardTestDispatcher(testScheduler))
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
