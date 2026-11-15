package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import warlockfe.warlock3.core.client.ClientEvent
import warlockfe.warlock3.core.client.CreatureStatus
import warlockfe.warlock3.core.client.Objective
import warlockfe.warlock3.core.client.PulseInfo
import warlockfe.warlock3.core.client.RoomMeta
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.StreamLineFeed
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowRegistry
import kotlin.time.Clock
import kotlin.time.Instant

/** A client with no server behind it: its state is whatever a test sets, and it records what is sent. */
open class FakeWarlockClient(
    private val windowRegistry: WindowRegistry,
) : WarlockClient {
    override val eventFlow = MutableSharedFlow<ClientEvent>(extraBufferCapacity = 256)

    var closed = false

    /** The game commands sent, in order. */
    val sentCommands = mutableListOf<String>()

    /** The commands sent straight to the server, bypassing type-ahead, in order. */
    val directCommands = mutableListOf<String>()

    /** The text of each line printed, in order. */
    val printed = mutableListOf<String>()

    var disconnectRequested = false

    /** The widget commands sent, in order. */
    val widgetCommands = mutableListOf<String>()

    /** The debug lines printed, in order. */
    val debugLines = mutableListOf<String>()

    override suspend fun connect(key: String) = Unit

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

    override fun disconnect() {
        disconnectRequested = true
    }

    override suspend fun sendCommand(line: String): SendCommandType {
        sentCommands += line
        return SendCommandType.COMMAND
    }

    override suspend fun sendCommandDirect(command: String) {
        directCommands += command
    }

    override suspend fun sendWidgetCommand(
        command: String,
        echo: String?,
    ) {
        widgetCommands += command
    }

    override fun requestMenu(
        exist: String,
        noun: String?,
    ) = 0

    override suspend fun print(message: StyledString) {
        printed += message.toText()
    }

    override suspend fun debug(message: String) {
        debugLines += message
    }

    override suspend fun scriptDebug(message: String) = Unit

    override suspend fun getStream(name: String): TextStream = windowRegistry.getOrCreateStream(name)

    override fun getComponents(): Map<String, StyledString> = emptyMap()

    override fun getComponent(name: String): StyledString? = null

    override fun setMaxTypeAhead(value: Int) = Unit

    override fun close() {
        closed = true
    }
}
