package cc.warlock.warlock3.core.client

import cc.warlock.warlock3.core.compass.DirectionType

sealed interface ClientEvent
data class ClientTextEvent(val text: String) : ClientEvent
data class ClientProgressBarEvent(val progressBarData: ProgressBarData) : ClientEvent
data class ClientCompassEvent(val directions: List<DirectionType>) : ClientEvent
object ClientNavEvent : ClientEvent
object ClientPromptEvent : ClientEvent
data class ClientActionEvent(val text: String, val command: () -> String) : ClientEvent
