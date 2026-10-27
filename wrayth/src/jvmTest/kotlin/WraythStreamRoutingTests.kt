import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.prefs.SettingsProblems
import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.models.ClientSettingEntity
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.LoggingRepository
import warlockfe.warlock3.core.text.ResolvedStyle
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.core.window.BackgroundFlash
import warlockfe.warlock3.core.window.PanelState
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.WindowMemoryUsage
import warlockfe.warlock3.core.window.WindowRegistry
import warlockfe.warlock3.wrayth.network.WraythClient
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Which stream game text lands in, checked against the real Wrayth client in utils/wrayth-lab: a
// <pushStream> to a window nobody declared is refused there, and the text stays in main.
class WraythStreamRoutingTests {
    private companion object {
        val WATCHED = listOf("main", "thoughts", "inv", "nowhere", "debug", "scriptoutput")
    }

    @Test
    fun aPushToAnUndeclaredStreamStaysInMainAndIsReportedOnce() {
        val (registry, events) =
            run(
                "<mode id=\"GAME\"/>",
                "<pushStream id='nowhere'/>a goblin from nowhere<popStream/>",
                "<pushStream id='nowhere'/>another goblin<popStream/>",
            )
        assertFalse(registry.streams.containsKey("nowhere"))
        val main = registry.lines("main")
        assertEquals(1, main.count { it.contains("never declared (\"nowhere\")") })
        assertTrue("a goblin from nowhere" in main)
        assertTrue("another goblin" in main)
        assertTrue(ClientTextEvent("a goblin from nowhere", "main") in events.main)
    }

    @Test
    fun aPushToADeclaredStreamGoesThere() {
        val (registry, events) =
            run(
                "<mode id=\"GAME\"/>",
                "<streamWindow id='thoughts' title='Thoughts'/>",
                "<streamWindow id='inv' title='Inventory' ifClosed=''/>",
                "<pushStream id='thoughts'/>a goblin thinks<popStream/>",
                "<pushStream id='inv'/>a goblin figurine<popStream/>",
            )
        assertEquals(listOf("a goblin thinks"), registry.lines("thoughts"))
        assertEquals(listOf("a goblin figurine"), registry.lines("inv"))
        assertFalse(registry.lines("main").any { it.contains("never declared") })
        // Every stream's line is on streamLines; only those that fall back to main reach the
        // events triggers and matches hear: no ifClosed means main, ifClosed='' means nowhere.
        assertTrue(ClientTextEvent("a goblin thinks", "thoughts") in events.streams)
        assertTrue(ClientTextEvent("a goblin figurine", "inv") in events.streams)
        assertTrue(ClientTextEvent("a goblin thinks", "thoughts") in events.main)
        assertFalse(events.main.any { it.text == "a goblin figurine" })
    }

    @Test
    fun printedTextReachesScriptsAsPlainText() {
        val (_, events) = run("<mode id=\"GAME\"/>") { it.print(StyledString("ready", WarlockStyle.Echo)) }
        assertTrue(ClientTextEvent("ready", "main") in events.main)
        assertTrue(ClientTextEvent("ready", "main") in events.streams)
    }

    @Test
    fun theClientsOwnStreamsAreOnStreamLinesOnly() {
        val (_, events) = run("<mode id=\"GAME\"/>") { it.scriptDebug("from a script") }
        assertTrue(ClientTextEvent("from a script", "scriptoutput") in events.streams)
        assertFalse(events.main.any { it.text == "from a script" })
        // Every line from the server is echoed to the debug stream.
        assertTrue(ClientTextEvent("<mode id=\"GAME\"/>", "debug") in events.streams)
    }

    /** The text events a client sent: on eventFlow, and on streamLines for the [WATCHED] streams. */
    private class TextEvents(
        val main: List<ClientTextEvent>,
        val streams: List<ClientTextEvent>,
    )

    /**
     * Feeds [lines] to a client, as the whole of what the server sends, and returns what it did.
     * [beforeConnect] gets the client once its events are being collected.
     */
    private fun run(
        vararg lines: String,
        beforeConnect: suspend (WraythClient) -> Unit = {},
    ): Pair<RecordingWindowRegistry, TextEvents> =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val tempDir = Files.createTempDirectory("wrayth-routing").toString()
            val configStore = ClientConfigStore(tempDir, SystemFileSystem)
            configStore.mutateClient { it.copy(logType = "NONE") }
            val dirs = WarlockDirs(homeDir = tempDir, dataDir = tempDir, configDir = tempDir, logDir = tempDir)
            val settings = ClientSettingRepository(InMemoryClientSettingDao(), configStore, dirs, SettingsProblems(tempDir))
            val registry = RecordingWindowRegistry()
            val client =
                WraythClient(
                    characterRepository = CharacterRepository(configStore),
                    windowRegistry = registry,
                    fileLogging = LoggingRepository(settings, scope),
                    ioDispatcher = Dispatchers.IO,
                    socket = ScriptedSocket(lines.toList()),
                    commandListStore = null,
                )
            val main = mutableListOf<ClientTextEvent>()
            val streams = mutableListOf<ClientTextEvent>()
            val mainSubscribed = CompletableDeferred<Unit>()
            scope.launch {
                client.eventFlow.onSubscription { mainSubscribed.complete(Unit) }.collect { event ->
                    if (event is ClientTextEvent) {
                        synchronized(main) { main += event }
                    }
                }
            }
            // Undispatched, so each is subscribed before the client connects.
            for (stream in WATCHED) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    client.streamLines(stream).collect { event ->
                        synchronized(streams) { streams += event }
                    }
                }
            }
            mainSubscribed.await()
            beforeConnect(client)
            client.connect("key")
            withTimeout(10.seconds) { client.disconnected.first { it } }
            scope.cancel()
            registry to TextEvents(synchronized(main) { main.toList() }, synchronized(streams) { streams.toList() })
        }
}

/** Serves [lines] in order and then reports the connection closed. */
private class ScriptedSocket(
    lines: List<String>,
) : WarlockSocket {
    private val incoming = Channel<String>(Channel.UNLIMITED)

    init {
        lines.forEach { incoming.trySend(it) }
        incoming.close()
    }

    override var isClosed: Boolean = false
        private set

    override suspend fun connect(
        host: String,
        port: Int,
    ) = Unit

    override suspend fun readLine(): String? = incoming.receiveCatching().getOrNull()

    override suspend fun readAvailable(min: Int): String? = readLine()?.let { it + "\n" }

    override fun ready(): Boolean = !incoming.isEmpty

    override suspend fun write(text: String) = Unit

    override fun close() {
        isClosed = true
    }
}

/** Keeps the whole lines appended to each stream, as plain text. */
private class RecordingWindowRegistry : WindowRegistry {
    val streams = ConcurrentHashMap<String, RecordingStream>()

    fun lines(stream: String): List<String> = streams[stream]?.lines?.toList() ?: emptyList()

    override val presets: StateFlow<Map<String, StyleLayer>> = MutableStateFlow(emptyMap())
    override val baseStyle: StateFlow<ResolvedStyle> = MutableStateFlow(ResolvedStyle())
    override val colorPalette: StateFlow<Map<String, WarlockColor>> = MutableStateFlow(emptyMap())
    override val backgroundFlashes: StateFlow<Map<String, BackgroundFlash>> = MutableStateFlow(emptyMap())

    override fun getOrCreateStream(name: String): TextStream = streams.computeIfAbsent(name) { RecordingStream(it) }

    override fun getStreams(): Collection<TextStream> = streams.values

    override suspend fun updateComponent(
        name: String,
        value: StyledString,
    ) = Unit

    override fun getOrCreatePanel(name: String): PanelState =
        object : PanelState {
            override val id = name

            override suspend fun setObject(value: PanelObject) = Unit

            override suspend fun clear() = Unit

            override suspend fun updateState() = Unit
        }

    override suspend fun memoryUsage(): WindowMemoryUsage = WindowMemoryUsage.EMPTY

    override fun flashBackground(
        window: String,
        color: WarlockColor,
        total: Duration,
        fadeIn: Duration,
        fadeOut: Duration,
    ) = Unit

    override fun setCharacterId(characterId: String) = Unit

    override fun close() = Unit
}

private class RecordingStream(
    override val id: String,
) : TextStream {
    val lines: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    override suspend fun appendPartial(
        text: StyledString,
        isPrompt: Boolean,
    ) = Unit

    override suspend fun appendPartialAndEol(text: StyledString) {
        lines += text.toText()
    }

    override suspend fun clear() = lines.clear()

    override suspend fun appendLine(
        text: StyledString,
        ignoreWhenBlank: Boolean,
        showWhenClosed: String?,
    ) {
        lines += text.toText()
    }

    override suspend fun updateComponent(
        name: String,
        value: StyledString,
    ) = Unit

    override suspend fun appendResource(url: String) = Unit

    override fun showTimestamps(value: Boolean) = Unit

    override fun setApplyStyling(value: Boolean) = Unit

    override fun setNameFilter(value: Boolean) = Unit
}

/** In-memory [ClientSettingDao], so the settings repository needs no SQLite. */
private class InMemoryClientSettingDao : ClientSettingDao {
    private val values = ConcurrentHashMap<String, String>()

    override suspend fun getAll(): List<ClientSettingEntity> = values.map { ClientSettingEntity(it.key, it.value) }

    override suspend fun getByKey(key: String): String? = values[key]

    override fun observeByKey(key: String): Flow<String?> = MutableStateFlow(values[key])

    override suspend fun removeByKey(key: String) {
        values.remove(key)
    }

    override suspend fun save(entity: ClientSettingEntity) {
        val value = entity.value
        if (value == null) values.remove(entity.key) else values[entity.key] = value
    }
}
