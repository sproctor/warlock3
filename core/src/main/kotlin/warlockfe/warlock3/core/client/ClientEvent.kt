package warlockfe.warlock3.core.client

import warlockfe.warlock3.core.compass.DirectionType
import kotlinx.collections.immutable.ImmutableSet

sealed interface ClientEvent
data class ClientTextEvent(val text: String) : ClientEvent
data class ClientProgressBarEvent(val progressBarData: ProgressBarData) : ClientEvent
data class ClientCompassEvent(val directions: ImmutableSet<DirectionType>) : ClientEvent
object ClientNavEvent : ClientEvent
object ClientPromptEvent : ClientEvent
data class ClientActionEvent(val text: String, val command: () -> String) : ClientEvent
