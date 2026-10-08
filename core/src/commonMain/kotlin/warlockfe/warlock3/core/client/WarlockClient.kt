package warlockfe.warlock3.core.client

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.WindowInfo
import kotlin.time.Instant

interface WarlockClient {
    val eventFlow: SharedFlow<ClientEvent>

    val roundTimeEnd: StateFlow<Long?>
    val castTimeEnd: StateFlow<Long?>
    val gameName: StateFlow<String?>
    val characterName: StateFlow<String?>
    val leftHand: StateFlow<String?>
    val rightHand: StateFlow<String?>
    val spellHand: StateFlow<String?>

    val indicators: StateFlow<Set<String>>

    val menuData: StateFlow<WarlockMenuData>

    val characterId: StateFlow<String?>
    val windowInfo: StateFlow<List<WindowInfo>>

    fun getCurrentTime(): Instant

    val disconnected: StateFlow<Boolean>

    suspend fun connect(key: String)

    fun disconnect()

    suspend fun sendCommand(line: String): SendCommandType

    suspend fun sendCommandDirect(command: String)

    /**
     * Sends [line] as [sendCommand] does, queued behind the type-ahead limit, but without echoing it
     * to the main window. A client that cannot leave the echo out sends it the ordinary way.
     */
    suspend fun sendQuietCommand(line: String): SendCommandType = sendCommand(line)

    /**
     * Has [interceptor] look at each line of game text bound for the main window before it is shown,
     * and keep the ones it claims off the screen and away from [eventFlow]; logs still have them.
     * Returns the function that removes it. Interceptors are called on the connection's read loop,
     * so they must be quick and must not block. A client that cannot intercept lines ignores this.
     */
    fun addLineInterceptor(interceptor: LineInterceptor): () -> Unit = {}

    /**
     * Hands each line of the game's protocol to [listener] as it arrives, markup and all, before
     * the client parses it: for scripts that keep track of the game from the protocol themselves.
     * The tap's [ProtocolTap.history] is the lines that came before, the most recent few thousand
     * of the connection, and every line after them goes to the listener, exactly once and in order.
     * The listener is called on the connection's read loop, so it must be quick and must not block.
     * A client with no markup protocol has no lines to give.
     */
    fun tapProtocol(listener: (String) -> Unit): ProtocolTap = ProtocolTap(history = emptyList(), remove = {})

    // A panel widget's command: sends [command] to the server verbatim without the normal command
    // echo, showing [echo] as the echoed text when present (Wrayth semantics).
    suspend fun sendWidgetCommand(
        command: String,
        echo: String?,
    )

    // Requests the server-driven context menu for [exist] (a menuLink/menuImage widget or a text
    // link). Returns the menu id; the response arrives via [menuData].
    fun requestMenu(
        exist: String,
        noun: String?,
    ): Int

    suspend fun print(message: StyledString)

    suspend fun debug(message: String)

    suspend fun scriptDebug(message: String)

    suspend fun getStream(name: String): TextStream

    fun getComponents(): Map<String, StyledString>

    fun getComponent(name: String): StyledString?

    fun setMaxTypeAhead(value: Int)

    fun close()
}
