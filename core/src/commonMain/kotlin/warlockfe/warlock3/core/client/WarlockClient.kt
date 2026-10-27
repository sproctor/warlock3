package warlockfe.warlock3.core.client

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.WindowInfo
import kotlin.time.Instant

interface WarlockClient {
    // Text events here are the main window's lines: main-stream text, and that of any stream that
    // falls back to main when its window is closed, whether or not that window is open. As in
    // Wrayth, whose matches hear an open Thoughts window (no ifClosed, so main) but never a stream
    // declared ifClosed=''.
    val eventFlow: SharedFlow<ClientEvent>

    // The completed lines of every stream, main or not, for whoever wants one window's text: the
    // game's, and those the client writes itself (print, debug, script output). Not prompts or
    // partial lines, and not text shown while the game has the parser off (raw mode, as in
    // character creation), none of which [eventFlow] carries either. Lines are only sent while
    // something is collecting, so nobody pays for them otherwise.
    val streamLines: SharedFlow<ClientTextEvent>

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
