import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.client.ClientCompassEvent
import warlockfe.warlock3.core.client.ClientEvent
import warlockfe.warlock3.core.client.ClientGmcpEvent
import warlockfe.warlock3.core.client.ClientPromptEvent
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.client.MudScriptOffer
import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.core.compass.Direction
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
import warlockfe.warlock3.core.text.StyledStringSubstring
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.core.window.BackgroundFlash
import warlockfe.warlock3.core.window.PanelState
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.WindowLocation
import warlockfe.warlock3.core.window.WindowMemoryUsage
import warlockfe.warlock3.core.window.WindowRegistry
import warlockfe.warlock3.telnet.network.TelnetClient
import warlockfe.warlock3.telnet.network.TelnetSocket
import warlockfe.warlock3.telnet.protocol.TelnetDecoder
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The client against a real TCP server on loopback: what the MUD sends ends up in the main window
 * as lines, prompts and styles, and what the user sends goes out the way a MUD expects.
 */
class TelnetClientTests {
    private val esc = "\u001B"
    private lateinit var dir: Path
    private lateinit var scope: CoroutineScope
    private lateinit var selector: SelectorManager
    private lateinit var server: ServerSocket
    private lateinit var registry: FakeWindowRegistry
    private lateinit var client: TelnetClient
    private lateinit var peer: Socket
    private lateinit var fromClient: ByteReadChannel
    private lateinit var toClient: ByteWriteChannel
    private val events = mutableListOf<ClientEvent>()

    @BeforeTest
    fun setUp() =
        runBlocking<Unit> {
            dir = Path(Files.createTempDirectory("telnet-client-test").toAbsolutePath().toString())
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            selector = SelectorManager(Dispatchers.IO)
            server = aSocket(selector).tcp().bind("127.0.0.1", 0)
            connectClient(acceptScripts = true)
        }

    /** Makes a client, connects it to the server, and takes the server's end of the connection. */
    private suspend fun connectClient(acceptScripts: Boolean) =
        coroutineScope {
            val port = (server.localAddress as InetSocketAddress).port

            val configStore = ClientConfigStore(dir.toString(), SystemFileSystem).also { it.load() }
            val clientSettings =
                ClientSettingRepository(
                    clientSettingDao = UnusedClientSettingDao,
                    clientConfigStore = configStore,
                    warlockDirs =
                        WarlockDirs(
                            homeDir = dir.toString(),
                            dataDir = dir.toString(),
                            configDir = dir.toString(),
                            logDir = Path(dir, "logs").toString(),
                        ),
                    settingsProblems = SettingsProblems(dir.toString()),
                )
            registry = FakeWindowRegistry()
            val socket = TelnetSocket(Dispatchers.IO)
            client =
                TelnetClient(
                    gameCode = "mud.example",
                    character = "Bob",
                    acceptScripts = acceptScripts,
                    characterRepository = CharacterRepository(configStore),
                    windowRegistry = registry,
                    fileLogging = LoggingRepository(clientSettings, scope),
                    ioDispatcher = Dispatchers.IO,
                    socket = socket,
                )
            scope.launch { client.eventFlow.collect { synchronized(events) { events += it } } }

            val accepted = async { server.accept() }
            socket.connect("127.0.0.1", port, tls = false)
            peer = accepted.await()
            fromClient = peer.openReadChannel()
            toClient = peer.openWriteChannel(autoFlush = true)
            client.connect("")
        }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        client.close()
        peer.close()
        server.close()
        selector.close()
        scope.cancel()
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private suspend fun send(text: String) = send(text.encodeToByteArray())

    private suspend fun send(bytes: ByteArray) {
        toClient.writeFully(bytes)
        toClient.flush()
    }

    private suspend fun receive(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var read = 0
        withTimeout(5.seconds) {
            while (read < count) {
                val n = fromClient.readAvailable(buffer, read, count - read)
                check(n >= 0) { "peer closed" }
                read += n
            }
        }
        return buffer
    }

    private suspend fun <T> await(
        description: String,
        block: () -> T?,
    ): T =
        withTimeout(5.seconds) {
            var value = block()
            while (value == null) {
                delay(10)
                value = block()
            }
            value
        }.also { requireNotNull(it) { description } }

    private fun mainCalls(): List<StreamCall> = registry.stream("main").calls()

    private fun eventsSnapshot(): List<ClientEvent> = synchronized(events) { events.toList() }

    @Test
    fun characterIsIdentifiedFromTheConnection() {
        assertEquals("mud.example:bob", client.characterId.value)
        assertEquals("mud.example", client.gameName.value)
        assertEquals("Bob", client.characterName.value)
        assertEquals("mud.example:bob", registry.assignedCharacterId)
    }

    @Test
    fun completeLinesGoToTheMainWindowWithTheirColors() =
        runBlocking<Unit> {
            send("$esc[31mHello$esc[0m world\r\nSecond line\r\n")
            val lines = await("two lines") { mainCalls().filterIsInstance<StreamCall.Line>().takeIf { it.size == 2 } }
            val first = lines[0].text.substrings.map { it as StyledStringSubstring }
            assertEquals(listOf("Hello", " world"), first.map { it.text })
            assertEquals(listOf("ansi.red"), first[0].styles.map { it.name })
            assertEquals(emptyList(), first[1].styles)
            assertEquals("Second line", lines[1].text.toText())

            val texts = await("text events") { eventsSnapshot().filterIsInstance<ClientTextEvent>().takeIf { it.size == 2 } }
            assertEquals(listOf("Hello world", "Second line"), texts.map { it.text })
        }

    @Test
    fun aLineLeftHangingBecomesAPrompt() =
        runBlocking<Unit> {
            send("You are standing in a field.\r\nHP:10 > ")
            val prompt = await("prompt") { mainCalls().filterIsInstance<StreamCall.Partial>().firstOrNull() }
            assertEquals("HP:10 > ", prompt.text.toText())
            assertTrue(prompt.isPrompt)
            await("prompt event") { eventsSnapshot().firstOrNull { it is ClientPromptEvent } }
            Unit
        }

    @Test
    fun goAheadMarksThePromptAtOnce() =
        runBlocking<Unit> {
            send("> ".encodeToByteArray() + byteArrayOf(0xFF.toByte(), 0xF9.toByte()))
            val prompt = await("prompt") { mainCalls().filterIsInstance<StreamCall.Partial>().firstOrNull() }
            assertEquals("> ", prompt.text.toText())
            assertTrue(prompt.isPrompt)
        }

    @Test
    fun theCommandIsEchoedOntoThePromptLineAndSentWithCrLf() =
        runBlocking<Unit> {
            send("> ")
            await("prompt") { mainCalls().filterIsInstance<StreamCall.Partial>().firstOrNull() }
            client.sendCommand("look")
            assertContentEquals("look\r\n".encodeToByteArray(), receive(6))
            val echo = await("echo") { mainCalls().filterIsInstance<StreamCall.PartialAndEol>().firstOrNull() }
            assertEquals("look", echo.text.toText())
            assertEquals(
                listOf("command"),
                echo.text.substrings
                    .flatMap { it.styles }
                    .map { it.name },
            )

            // What the server says next starts a fresh line.
            send("You see nothing special.\r\n")
            val line = await("response") { mainCalls().filterIsInstance<StreamCall.Line>().firstOrNull() }
            assertEquals("You see nothing special.", line.text.toText())
        }

    @Test
    fun whileTheServerEchoesTheCommandIsNotShown() =
        runBlocking<Unit> {
            send(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 1)) // IAC WILL ECHO
            assertContentEquals(byteArrayOf(0xFF.toByte(), 0xFD.toByte(), 1), receive(3)) // IAC DO ECHO
            send("Password: ")
            await("prompt") { mainCalls().filterIsInstance<StreamCall.Partial>().firstOrNull() }
            client.sendCommand("hunter2")
            assertContentEquals("hunter2\r\n".encodeToByteArray(), receive(9))
            val echo = await("line end") { mainCalls().filterIsInstance<StreamCall.PartialAndEol>().firstOrNull() }
            assertEquals("", echo.text.toText())
            assertTrue(eventsSnapshot().none { it is ClientTextEvent && it.text == "hunter2" })
        }

    @Test
    fun negotiationIsAnswered() =
        runBlocking<Unit> {
            send(byteArrayOf(0xFF.toByte(), 0xFD.toByte(), 24)) // IAC DO TTYPE
            assertContentEquals(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 24), receive(3)) // IAC WILL TTYPE
        }

    @Test
    fun gmcpVitalsAndExitsReachThePanelAndCompass() =
        runBlocking<Unit> {
            send(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 201.toByte())) // IAC WILL GMCP
            assertContentEquals(byteArrayOf(0xFF.toByte(), 0xFD.toByte(), 201.toByte()), receive(3)) // IAC DO GMCP
            // Core.Hello and Core.Supports.Set follow; read them off so the stream is clean.
            receive(TelnetDecoder.gmcpMessage("Core.Hello", """{"client":"Warlock","version":"3"}""").size)
            receive(TelnetDecoder.gmcpMessage("Core.Supports.Set", """["Char 1","Char.Items 1","Room 1"]""").size)
            receive(TelnetDecoder.gmcpMessage("Char.Items.Inv", "").size)

            send(TelnetDecoder.gmcpMessage("Room.Info", """{"exits":{"n":1,"e":2}}"""))
            val compass = await("compass") { eventsSnapshot().filterIsInstance<ClientCompassEvent>().firstOrNull() }
            assertEquals(setOf(Direction("n"), Direction("e")), compass.directions.toSet())

            send(TelnetDecoder.gmcpMessage("Char.Vitals", """{"hp":"50","maxhp":"100","mp":"10","maxmp":"40"}"""))
            val bars =
                await("vitals") {
                    registry.panels["minivitals"]
                        ?.objects
                        ?.filterIsInstance<PanelObject.ProgressBar>()
                        ?.takeIf { it.isNotEmpty() }
                }
            assertEquals(listOf("health 50/100", "mana 10/40"), bars.map { it.text })
            assertEquals(listOf(50, 25), bars.map { it.value.value })
            // The panel is announced for the status bar, which is where the vitals are drawn.
            val info = client.windowInfo.value.first { it.name == "minivitals" }
            assertEquals(WindowLocation.STATBAR, info.location)

            send(
                TelnetDecoder.gmcpMessage(
                    "Char.Items.List",
                    """{"location":"inv","items":[{"id":"1","name":"a sword","attrib":"L"},{"id":"2","name":"a shield","attrib":"l"}]}""",
                ),
            )
            withTimeout(5.seconds) { client.rightHand.first { it == "a sword" } }
            assertEquals("a shield", client.leftHand.value)
        }

    /** Negotiates GMCP and reads the handshake off the stream, so what follows is clean. */
    private suspend fun negotiateGmcp() {
        send(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 201.toByte())) // IAC WILL GMCP
        assertContentEquals(byteArrayOf(0xFF.toByte(), 0xFD.toByte(), 201.toByte()), receive(3)) // IAC DO GMCP
        receive(TelnetDecoder.gmcpMessage("Core.Hello", """{"client":"Warlock","version":"3"}""").size)
        receive(TelnetDecoder.gmcpMessage("Core.Supports.Set", """["Char 1","Char.Items 1","Room 1"]""").size)
        receive(TelnetDecoder.gmcpMessage("Char.Items.Inv", "").size)
    }

    @Test
    fun theMudsScriptIsOfferedAndScriptsCanDriveTheClient() =
        runBlocking<Unit> {
            negotiateGmcp()
            send(TelnetDecoder.gmcpMessage("Client.GUI", """{"version": "3", "url": "https://mud.example/warlock.lua"}"""))
            val offer = withTimeout(5.seconds) { client.mudScript.first { it != null } }
            assertEquals(MudScriptOffer(version = "3", url = "https://mud.example/warlock.lua"), offer)
            // Every GMCP message is passed on for scripts, this one included.
            val passedOn = await("gmcp event") { eventsSnapshot().filterIsInstance<ClientGmcpEvent>().firstOrNull() }
            assertEquals(ClientGmcpEvent("Client.GUI", """{"version": "3", "url": "https://mud.example/warlock.lua"}"""), passedOn)

            // A script can talk GMCP back, and set the times nothing in the stream states.
            val message = TelnetDecoder.gmcpMessage("Core.Supports.Add", """["Char.Status 1"]""")
            client.sendGmcp("Core.Supports.Add", """["Char.Status 1"]""")
            assertContentEquals(message, receive(message.size))
            client.setRoundTime(1234L)
            client.setCastTime(1235L)
            assertEquals(1234L, client.roundTimeEnd.value)
            assertEquals(1235L, client.castTimeEnd.value)
            client.flashBackground("main", WarlockColor("#400000"), 1.5.seconds, 0.5.seconds, 0.25.seconds)
            assertEquals(
                BackgroundFlash(WarlockColor("#400000"), 1.5.seconds, 0.5.seconds, 0.25.seconds, 0L),
                registry.backgroundFlashes.value["main"],
            )
        }

    @Test
    fun gmcpIsNotSentToAServerThatDidNotNegotiateIt() =
        runBlocking<Unit> {
            client.sendGmcp("Core.Supports.Add", """["Char.Status 1"]""")
            client.sendCommandDirect("look")
            // The command is the first thing the server sees.
            assertContentEquals("look\r\n".encodeToByteArray(), receive(6))
        }

    @Test
    fun aConnectionThatDeclinesScriptsIgnoresTheOffer() =
        runBlocking<Unit> {
            client.close()
            peer.close()
            connectClient(acceptScripts = false)
            negotiateGmcp()
            send(TelnetDecoder.gmcpMessage("Client.GUI", """{"version": "3", "url": "https://mud.example/warlock.lua"}"""))
            send(TelnetDecoder.gmcpMessage("Core.Ping", ""))
            // Once the ping has been seen, the offer before it has been too.
            await("ping") { eventsSnapshot().filterIsInstance<ClientGmcpEvent>().firstOrNull { it.name == "Core.Ping" } }
            assertNull(client.mudScript.value)
        }

    @Test
    fun theServerHangingUpIsReported() =
        runBlocking<Unit> {
            send("Goodbye.\r\n")
            await("line") { mainCalls().filterIsInstance<StreamCall.Line>().firstOrNull() }
            peer.close()
            withTimeout(5.seconds) { client.disconnected.first { it } }
            val lines = await("closed line") { mainCalls().filterIsInstance<StreamCall.Line>().takeIf { it.size == 2 } }
            assertEquals("Connection closed by server.", lines[1].text.toText())
        }
}

private sealed interface StreamCall {
    data class Line(
        val text: StyledString,
    ) : StreamCall

    data class Partial(
        val text: StyledString,
        val isPrompt: Boolean,
    ) : StreamCall

    data class PartialAndEol(
        val text: StyledString,
    ) : StreamCall
}

private class FakeTextStream(
    override val id: String,
) : TextStream {
    private val recorded = mutableListOf<StreamCall>()

    fun calls(): List<StreamCall> = synchronized(recorded) { recorded.toList() }

    private fun record(call: StreamCall) = synchronized(recorded) { recorded += call }

    override suspend fun appendPartial(
        text: StyledString,
        isPrompt: Boolean,
    ) = record(StreamCall.Partial(text, isPrompt))

    override suspend fun appendPartialAndEol(text: StyledString) = record(StreamCall.PartialAndEol(text))

    override suspend fun clear() {}

    override suspend fun appendLine(
        text: StyledString,
        ignoreWhenBlank: Boolean,
        showWhenClosed: String?,
    ) = record(StreamCall.Line(text))

    override suspend fun updateComponent(
        name: String,
        value: StyledString,
    ) {}

    override suspend fun appendResource(url: String) {}

    override fun showTimestamps(value: Boolean) {}

    override fun setApplyStyling(value: Boolean) {}

    override fun setNameFilter(value: Boolean) {}
}

private class FakePanelState(
    override val id: String,
) : PanelState {
    private val pending = mutableListOf<PanelObject>()

    @Volatile
    var objects: List<PanelObject> = emptyList()
        private set

    override suspend fun setObject(value: PanelObject) {
        pending += value
    }

    override suspend fun clear() {
        pending.clear()
    }

    override suspend fun updateState() {
        objects = pending.toList()
    }
}

private class FakeWindowRegistry : WindowRegistry {
    private val streams = mutableMapOf<String, FakeTextStream>()
    val panels = mutableMapOf<String, FakePanelState>()
    var assignedCharacterId: String? = null

    fun stream(name: String): FakeTextStream = getOrCreateStream(name) as FakeTextStream

    override val presets: StateFlow<Map<String, StyleLayer>> = MutableStateFlow(emptyMap())
    override val baseStyle: StateFlow<ResolvedStyle> get() = error("unused")
    override val colorPalette: StateFlow<Map<String, WarlockColor>> = MutableStateFlow(emptyMap())
    override val backgroundFlashes = MutableStateFlow<Map<String, BackgroundFlash>>(emptyMap())

    override fun flashBackground(
        window: String,
        color: WarlockColor,
        total: Duration,
        fadeIn: Duration,
        fadeOut: Duration,
    ) {
        backgroundFlashes.value += window to BackgroundFlash(color, total, fadeIn, fadeOut, backgroundFlashes.value.size.toLong())
    }

    override fun getOrCreateStream(name: String): TextStream = synchronized(streams) { streams.getOrPut(name) { FakeTextStream(name) } }

    override fun getStreams(): Collection<TextStream> = synchronized(streams) { streams.values.toList() }

    override suspend fun updateComponent(
        name: String,
        value: StyledString,
    ) {}

    override fun getOrCreatePanel(name: String): PanelState = synchronized(panels) { panels.getOrPut(name) { FakePanelState(name) } }

    override suspend fun memoryUsage(): WindowMemoryUsage = error("unused")

    override fun setCharacterId(characterId: String) {
        assignedCharacterId = characterId
    }

    override fun close() {}
}

private object UnusedClientSettingDao : ClientSettingDao {
    override suspend fun getAll(): List<ClientSettingEntity> = error("unused")

    override suspend fun getByKey(key: String): String? = error("unused")

    override fun observeByKey(key: String): Flow<String?> = flowOf(null)

    override suspend fun removeByKey(key: String) = error("unused")

    override suspend fun save(entity: ClientSettingEntity) = error("unused")
}
