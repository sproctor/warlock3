package warlockfe.warlock3.core.client

import com.eygraber.uri.Uri
import kotlinx.collections.immutable.ImmutableSet
import warlockfe.warlock3.core.compass.Direction
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowLocation

sealed interface ClientEvent

/**
 * A line of game text, from the stream [streamId] (null on a connection with only the one
 * stream). [inMain] says whether the line belongs to the main window: main-stream text, or text
 * from a stream that falls back to main when its window is closed, whether or not that window is
 * open. Triggers and match waits only see those lines, as in Wrayth, whose matches hear an open
 * Thoughts window (no `ifClosed`, so main) but not the inventory (`ifClosed=''`).
 */
data class ClientTextEvent(
    val text: String,
    val streamId: String?,
    val inMain: Boolean = true,
) : ClientEvent

data class ClientCompassEvent(
    val directions: ImmutableSet<Direction>,
) : ClientEvent

data object ClientNavEvent : ClientEvent

data object ClientPromptEvent : ClientEvent

/**
 * A GMCP message from the server, as sent: the package and message name (`Char.Vitals`) and the
 * JSON, empty when the message carried none. Every message is passed on, whether or not the
 * client made anything of it, so a script can act on the ones the client does not know.
 */
data class ClientGmcpEvent(
    val name: String,
    val data: String,
) : ClientEvent

data class ClientOpenUrlEvent(
    val url: Uri,
) : ClientEvent

data class ClientWindowInfoEvent(
    val info: WindowInfo,
) : ClientEvent

/**
 * The game asked for a window to be shown. Where it goes is not part of this: the announced
 * location rides on the window's [warlockfe.warlock3.core.window.WindowInfo], which is what the
 * docking bridge reads when the character has no saved spot for it. A panel the game puts in the
 * client's own chrome (`statBar`, `quickBar`) never raises this event at all.
 */
data class ClientOpenWindowEvent(
    val name: String,
) : ClientEvent

/** The game asked for a window to be closed. */
data class ClientCloseWindowEvent(
    val name: String,
) : ClientEvent
