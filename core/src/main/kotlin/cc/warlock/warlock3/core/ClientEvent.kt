package cc.warlock.warlock3.core

import cc.warlock.warlock3.core.compass.DirectionType

sealed class ClientEvent
data class ClientTextEvent(val text: String) : ClientEvent()
data class ClientProgressBarEvent(val progressBarData: ProgressBarData) : ClientEvent()
data class ClientCompassEvent(val directions: List<DirectionType>) : ClientEvent()
object ClientNavEvent : ClientEvent()
object ClientPromptEvent : ClientEvent()
