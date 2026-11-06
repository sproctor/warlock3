package warlockfe.warlock3.compose.ui.game

import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.GameConnection
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.testAppContainer
import warlockfe.warlock3.core.client.ClientCompassEvent
import warlockfe.warlock3.core.client.ClientEvent
import warlockfe.warlock3.core.client.CreatureStatus
import warlockfe.warlock3.core.client.Objective
import warlockfe.warlock3.core.client.PulseInfo
import warlockfe.warlock3.core.client.RoomMeta
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.StreamLineFeed
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.compass.Direction
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowRegistry
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The game screen has to hear what the server sends the moment it is connected. The client's
 * eventFlow has no replay, and its read loop starts on connect, so a game screen that subscribed
 * afterwards - or only queued its subscription on a busy main thread - lost the first compass and
 * the windows the game opens on login, and nothing ever sent them again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelClientEventsTest {
    private lateinit var dir: java.nio.file.Path
    private lateinit var database: PrefsDatabase

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("game-view-model-events-test")
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        if (::database.isInitialized) database.close()
        dir.deleteRecursively()
    }

    /**
     * A client whose read loop emits [firstEvent] as soon as it starts, before anything else runs, or
     * whose connect throws [failure] if there is one.
     */
    private class EagerClient(
        private val firstEvent: ClientEvent,
        private val windowRegistry: WindowRegistry,
        private val failure: Exception? = null,
    ) : WarlockClient {
        override val eventFlow = MutableSharedFlow<ClientEvent>(extraBufferCapacity = 256)

        var closed = false

        override suspend fun connect(key: String) {
            failure?.let { throw it }
            eventFlow.emit(firstEvent)
        }

        private val streamLineFeed = StreamLineFeed()

        override fun streamLines(stream: String) = streamLineFeed.linesOf(stream)

        override val roundTimeEnd = MutableStateFlow<Long?>(null)
        override val castTimeEnd = MutableStateFlow<Long?>(null)
        override val gameName = MutableStateFlow<String?>(null)
        override val characterName = MutableStateFlow<String?>(null)
        override val leftHand = MutableStateFlow<String?>(null)
        override val rightHand = MutableStateFlow<String?>(null)
        override val spellHand = MutableStateFlow<String?>(null)
        override val indicators = MutableStateFlow<Set<String>>(emptySet())
        override val menuData = MutableStateFlow(WarlockMenuData(0, emptyList()))
        override val characterId = MutableStateFlow<String?>(null)
        override val windowInfo = MutableStateFlow<List<WindowInfo>>(emptyList())
        override val disconnected = MutableStateFlow(false)
        override val roomId = MutableStateFlow<String?>(null)
        override val roomMeta = MutableStateFlow<RoomMeta?>(null)
        override val creatures = MutableStateFlow<Map<String, CreatureStatus>>(emptyMap())
        override val lastPulse = MutableStateFlow<PulseInfo?>(null)
        override val objectives = MutableStateFlow<List<Objective>>(emptyList())

        override fun getCurrentTime(): Instant = Clock.System.now()

        override fun disconnect() = Unit

        override suspend fun sendCommand(line: String) = SendCommandType.COMMAND

        override suspend fun sendCommandDirect(command: String) = Unit

        override suspend fun sendWidgetCommand(
            command: String,
            echo: String?,
        ) = Unit

        override fun requestMenu(
            exist: String,
            noun: String?,
        ) = 0

        override suspend fun print(message: StyledString) = Unit

        override suspend fun debug(message: String) = Unit

        override suspend fun scriptDebug(message: String) = Unit

        override suspend fun getStream(name: String): TextStream = windowRegistry.getOrCreateStream(name)

        override fun getComponents(): Map<String, StyledString> = emptyMap()

        override fun getComponent(name: String): StyledString? = null

        override fun setMaxTypeAhead(value: Int) = Unit

        override fun close() {
            closed = true
        }
    }

    private suspend fun appContainer(ioDispatcher: CoroutineDispatcher): AppContainer =
        testAppContainer(dir, ioDispatcher).also { database = it.database }

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
}
