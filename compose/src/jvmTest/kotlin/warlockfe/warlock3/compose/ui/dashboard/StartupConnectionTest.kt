package warlockfe.warlock3.compose.ui.dashboard

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.model.StartupConnection
import warlockfe.warlock3.compose.testAppContainer
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.mudmobile.SyncStatus
import warlockfe.warlock3.core.sge.SgeSettings
import warlockfe.warlock3.core.sge.SimuGameCredentials
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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * A window's startup connection (one named on the command line, a replayed log, or the last
 * connection) is opened by its dashboard once it is on screen, rather than before the window opens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StartupConnectionTest {
    private lateinit var dir: java.nio.file.Path
    private var container: AppContainer? = null

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("startup-connection-test")
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        container?.database?.close()
        dir.deleteRecursively()
    }

    /** A game log being replayed: [lines], then the end of the stream. */
    private class ReplaySocket(
        lines: List<String>,
    ) : WarlockSocket {
        private val remaining = ArrayDeque(lines)

        override var isClosed = false

        override suspend fun connect(
            host: String,
            port: Int,
        ) = Unit

        override suspend fun readLine(): String? = remaining.removeFirstOrNull()

        override suspend fun readAvailable(min: Int): String? = readLine()

        override fun ready(): Boolean = remaining.isNotEmpty()

        override suspend fun write(text: String) = Unit

        override fun close() {
            isClosed = true
        }
    }

    private suspend fun dashboard(
        gameState: GameState,
        ioDispatcher: CoroutineDispatcher,
    ): Pair<AppContainer, DashboardViewModel> {
        val appContainer = testAppContainer(dir, ioDispatcher).also { container = it }
        val sgeSettings = SgeSettings(host = "127.0.0.1", port = 1, certificate = ByteArray(0), secure = false)
        return appContainer to appContainer.dashboardViewModelFactory.create(gameState, sgeSettings)
    }

    @Test
    fun aStartupConnectionIsHandedOutOnce() {
        val stream = StartupConnection.Stream { ReplaySocket(emptyList()) }
        val gameState = GameState(stream)
        assertSame(stream, gameState.takeStartupConnection())
        // The dashboard comes back after a disconnect: that must not connect again.
        assertNull(gameState.takeStartupConnection())

        assertEquals(StartupConnection.LastConnection, GameState().takeStartupConnection())
        assertNull(GameState(startupConnection = null).takeStartupConnection())
    }

    @Test
    fun aReplayedLogGoesOnScreen() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val socket = ReplaySocket(listOf("Welcome back.", "Obvious paths: north."))
            val gameState = GameState(StartupConnection.Stream { socket })
            val (_, viewModel) = dashboard(gameState, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()

            val game = assertIs<GameScreen.ConnectedGameState>(gameState.screen).viewModel
            assertNull(viewModel.message)
            assertNull(viewModel.connectError)
            game.close()
            advanceUntilIdle()
        }

    @Test
    fun aReplayDoesNotStartTheSettingsSync() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val appContainer = testAppContainer(dir, StandardTestDispatcher(testScheduler)).also { container = it }
            // A saved MUD Mobile account: the dashboard of a session of the user's would sync it.
            appContainer.clientSettings.putMudMobileToken("token")
            val gameState = GameState(StartupConnection.Stream { ReplaySocket(emptyList()) })
            val sgeSettings = SgeSettings(host = "127.0.0.1", port = 1, certificate = ByteArray(0), secure = false)
            val viewModel = appContainer.dashboardViewModelFactory.create(gameState, sgeSettings)
            advanceUntilIdle()

            val sync = viewModel.syncState.value
            assertEquals(SyncStatus.Idle, sync.status)
            assertNull(sync.message, "the replay started a settings sync")
            assertNull(viewModel.mudMobileMessage)
            (gameState.screen as? GameScreen.ConnectedGameState)?.viewModel?.close()
            advanceUntilIdle()
        }

    @Test
    fun aStreamThatCannotBeOpenedIsReported() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val gameState = GameState(StartupConnection.Stream { throw IOException("log is gone") })
            val (_, viewModel) = dashboard(gameState, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()

            assertEquals(GameScreen.Dashboard, gameState.screen)
            assertTrue(assertNotNull(viewModel.connectError).contains("log is gone"))
            assertNull(viewModel.message)
            assertEquals(false, viewModel.busy)
        }

    @Test
    fun aRefusedServerLeavesNoConnectingStatusBehind() =
        runTest {
            // A real refused connection needs real I/O.
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val closedPort = ServerSocket(0).use { it.localPort }
            val credentials = SimuGameCredentials(host = "127.0.0.1", port = closedPort, key = "key")
            val gameState = GameState(StartupConnection.Credentials(credentials))
            val (_, viewModel) = dashboard(gameState, Dispatchers.IO)

            withContext(Dispatchers.Default) {
                withTimeout(10.seconds) {
                    while (viewModel.busy || gameState.screen !is GameScreen.ErrorState) delay(20)
                }
            }

            // The error screen keeps the dashboard under it: going back must not find it still
            // saying it is connecting.
            assertIs<GameScreen.ErrorState>(gameState.screen)
            assertNull(viewModel.message)
        }
}
