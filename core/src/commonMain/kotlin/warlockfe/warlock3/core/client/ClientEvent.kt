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
