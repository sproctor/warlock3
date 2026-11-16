package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.GameConnection
import warlockfe.warlock3.compose.deleteTestDir
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.testAppContainer
import warlockfe.warlock3.core.client.ClientCompassEvent
import warlockfe.warlock3.core.client.ClientEvent
import warlockfe.warlock3.core.compass.Direction
import warlockfe.warlock3.core.window.WindowRegistry
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The game screen has to hear what the server sends the moment it is connected. The client's
 * eventFlow has no replay, and its read loop starts on connect, so a game screen that subscribed
 * afterwards - or only queued its subscription on a busy main thread - lost the first compass and
 * the windows the game opens on login, and nothing ever sent them again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelClientEventsTest {
    private lateinit var dir: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("game-view-model-events-test")
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        // The database stays open: a game's window registry queries it from flows that closing the
        // registry cancels but doesn't wait for, and a query still running when the database closes
        // crashes the JVM in native SQLite. Its files can go with it; the open handles don't mind.
        deleteTestDir(dir)
    }

    /**
     * A client whose read loop emits [firstEvent] as soon as it starts, before anything else runs, or
     * whose connect throws [failure] if there is one.
     */
    private class EagerClient(
        private val firstEvent: ClientEvent,
        windowRegistry: WindowRegistry,
        private val failure: Exception? = null,
    ) : FakeWarlockClient(windowRegistry) {
        override suspend fun connect(key: String) {
            failure?.let { throw it }
            eventFlow.emit(firstEvent)
        }
    }

    private suspend fun appContainer(ioDispatcher: CoroutineDispatcher): AppContainer = testAppContainer(dir, ioDispatcher)

    @Test
    fun anEventSentTheMomentTheClientStartsReachesTheGameScreen() =
        runTest {
            // A main thread and a background pool that run nothing until told to, as busy ones would:
            // anything the view model only queues there has not happened yet when the client starts
            // reading.
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val container = appContainer(StandardTestDispatcher(testScheduler))
            val exits = persistentHashSetOf(Direction("n"), Direction("e"), Direction("out"))
            val windowRegistry = container.windowRegistryFactory.create()
            val client = EagerClient(ClientCompassEvent(exits), windowRegistry)

            val gameState = GameState()

            container.gameViewModelFactory.start(
                connection = GameConnection(client, windowRegistry, key = "key", reconnect = null),
                gameState = gameState,
            )
            advanceUntilIdle()

            val viewModel = assertIs<GameScreen.ConnectedGameState>(gameState.screen).viewModel
            assertEquals(exits, viewModel.compassState.value)
            viewModel.close()
            advanceUntilIdle()
        }

    @Test
    fun aClientThatFailsToStartIsClosedAndNeverShown() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val container = appContainer(StandardTestDispatcher(testScheduler))
            val windowRegistry = container.windowRegistryFactory.create()
            val failure = IllegalStateException("no character")
            val client = EagerClient(ClientCompassEvent(persistentHashSetOf()), windowRegistry, failure)
            val gameState = GameState()

            val thrown =
                assertFailsWith<IllegalStateException> {
                    container.gameViewModelFactory.start(
                        connection = GameConnection(client, windowRegistry, key = "key", reconnect = null),
                        gameState = gameState,
                    )
                }
            advanceUntilIdle()

            assertSame(failure, thrown)
            assertTrue(client.closed)
            assertEquals(GameScreen.Dashboard, gameState.screen)
        }

    @Test
    fun closingSavesACommandSentJustBefore() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val container = appContainer(StandardTestDispatcher(testScheduler))
            val windowRegistry = container.windowRegistryFactory.create()
            val client = EagerClient(ClientCompassEvent(persistentHashSetOf()), windowRegistry)
            val gameState = GameState()
            container.gameViewModelFactory.start(
                connection = GameConnection(client, windowRegistry, key = "key", reconnect = null),
                gameState = gameState,
            )
            client.characterId.value = "char"
            advanceUntilIdle()
            val viewModel = assertIs<GameScreen.ConnectedGameState>(gameState.screen).viewModel

            viewModel.entryTextState.setTextAndPlaceCursorAtEnd("look")
            viewModel.submit()
            // Closed before the save that sending launched has run.
            viewModel.close()
            advanceUntilIdle()

            assertEquals(listOf("look"), container.commandHistoryRepository.load("char"))
        }
}
