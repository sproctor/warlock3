package warlockfe.warlock3.scripting.wsl

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import warlockfe.warlock3.core.client.ClientEvent
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.WindowInfo
import kotlin.time.Instant

/**
 * In-memory [WarlockClient] test double. Emit [ClientEvent]s with [emit] and read what the
 * script sent back via [sentCommands]/[printed].
 */
class FakeWarlockClient(
    characterId: String? = "testchar",
) : WarlockClient {
    override val eventFlow = MutableSharedFlow<ClientEvent>(extraBufferCapacity = 64)

    override val roundTimeEnd = MutableStateFlow<Long?>(null)
    override val castTimeEnd = MutableStateFlow<Long?>(null)
    override val gameName = MutableStateFlow<String?>(null)
    override val characterName = MutableStateFlow<String?>("Testchar")
    override val leftHand = MutableStateFlow<String?>(null)
    override val rightHand = MutableStateFlow<String?>(null)
    override val spellHand = MutableStateFlow<String?>(null)
    override val indicators = MutableStateFlow<Set<String>>(emptySet())
    override val menuData = MutableStateFlow(WarlockMenuData(0, emptyList()))
    override val characterId = MutableStateFlow(characterId)
    override val windowInfo = MutableStateFlow<List<WindowInfo>>(emptyList())
    override val disconnected = MutableStateFlow(false)

    val sentCommands = mutableListOf<String>()
    val printed = mutableListOf<StyledString>()
    val scriptDebugMessages = mutableListOf<String>()

    private var currentTime: Instant = Instant.fromEpochSeconds(0)

    fun setCurrentTime(time: Instant) {
        currentTime = time
    }

    suspend fun emit(event: ClientEvent) {
        eventFlow.emit(event)
    }

    override fun getCurrentTime(): Instant = currentTime

    override suspend fun connect(key: String) = Unit

    override fun disconnect() = Unit

    override suspend fun sendCommand(line: String): SendCommandType {
        sentCommands += line
        return SendCommandType.COMMAND
    }

    override suspend fun sendCommandDirect(command: String) {
        sentCommands += command
    }

    override suspend fun print(message: StyledString) {
        printed += message
    }

    override suspend fun debug(message: String) = Unit

    override suspend fun scriptDebug(message: String) {
        scriptDebugMessages += message
    }

    override suspend fun getStream(name: String): TextStream = throw NotImplementedError("getStream not used in tests")

    override fun getComponents(): Map<String, StyledString> = emptyMap()

    override fun getComponent(name: String): StyledString? = null

    override fun setMaxTypeAhead(value: Int) = Unit

    override fun close() = Unit
}
