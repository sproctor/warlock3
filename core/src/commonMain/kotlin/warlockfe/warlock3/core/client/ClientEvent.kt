package warlockfe.warlock3.core.client

import warlockfe.warlock3.core.compass.DirectionType
import kotlinx.collections.immutable.ImmutableSet
import java.net.URL

sealed interface ClientEvent
data class ClientTextEvent(val text: String) : ClientEvent
data class ClientProgressBarEvent(val progressBarData: ProgressBarData) : ClientEvent
data class ClientCompassEvent(val directions: ImmutableSet<DirectionType>) : ClientEvent
data object ClientNavEvent : ClientEvent
data object ClientPromptEvent : ClientEvent
data class ClientOpenUrlEvent(val url: URL) : ClientEvent
