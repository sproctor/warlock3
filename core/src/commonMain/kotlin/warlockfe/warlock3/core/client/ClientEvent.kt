package warlockfe.warlock3.core.client

import kotlinx.collections.immutable.ImmutableSet
import warlockfe.warlock3.core.compass.DirectionType
import java.net.URI

sealed interface ClientEvent
data class ClientTextEvent(val text: String) : ClientEvent
data class ClientDialogEvent(val id: String, val data: DialogObject) : ClientEvent
data class ClientCompassEvent(val directions: ImmutableSet<DirectionType>) : ClientEvent
data object ClientNavEvent : ClientEvent
data object ClientPromptEvent : ClientEvent
data class ClientOpenUrlEvent(val url: URI) : ClientEvent
