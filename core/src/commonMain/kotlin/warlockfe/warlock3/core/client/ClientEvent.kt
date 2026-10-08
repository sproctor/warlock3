package warlockfe.warlock3.core.client

import com.eygraber.uri.Uri
import kotlinx.collections.immutable.ImmutableSet
import warlockfe.warlock3.core.compass.Direction
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowLocation

sealed interface ClientEvent

/**
 * A line of game text bound for the main window. [links] are the objects linked in it, in order,
 * when the protocol marks them ("a <a exist=..>kobold</a> leaps from hiding").
 */
data class ClientTextEvent(
    val text: String,
    val links: List<TextLink> = emptyList(),
) : ClientEvent

/** An object linked in a line of text: its exist id (without the `#`), its noun, and the linked text. */
data class TextLink(
    val id: String,
    val noun: String?,
    val text: String,
)

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
