package warlockfe.warlock3.core.client

import com.eygraber.uri.Uri
import kotlinx.collections.immutable.ImmutableSet
import warlockfe.warlock3.core.compass.Direction
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowLocation

sealed interface ClientEvent

data class ClientTextEvent(
    val text: String,
) : ClientEvent

data class ClientCompassEvent(
    val directions: ImmutableSet<Direction>,
) : ClientEvent

data object ClientNavEvent : ClientEvent

data object ClientPromptEvent : ClientEvent

data class ClientOpenUrlEvent(
    val url: Uri,
) : ClientEvent

data class ClientWindowInfoEvent(
    val info: WindowInfo,
) : ClientEvent

/**
 * The game asked for a window to be shown. [location] is where the protocol wants it, which is only
 * used when the character has no saved location for the window; null means the game named somewhere
 * we do not dock panels, so the window is left closed.
 */
data class ClientOpenWindowEvent(
    val name: String,
    val location: WindowLocation?,
) : ClientEvent

/** The game asked for a window to be closed. */
data class ClientCloseWindowEvent(
    val name: String,
) : ClientEvent
