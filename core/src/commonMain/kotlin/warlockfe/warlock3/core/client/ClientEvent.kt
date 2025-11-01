package warlockfe.warlock3.core.client

import com.eygraber.uri.Uri
import kotlinx.collections.immutable.ImmutableSet
import warlockfe.warlock3.core.compass.DirectionType

sealed interface ClientEvent
data class ClientTextEvent(val text: String) : ClientEvent
data class ClientDialogEvent(val id: String, val data: DialogObject) : ClientEvent
data class ClientDialogClearEvent(val id: String) : ClientEvent
data class ClientCompassEvent(val directions: ImmutableSet<DirectionType>) : ClientEvent
data object ClientNavEvent : ClientEvent
data object ClientPromptEvent : ClientEvent
data class ClientOpenUrlEvent(val url: Uri) : ClientEvent
