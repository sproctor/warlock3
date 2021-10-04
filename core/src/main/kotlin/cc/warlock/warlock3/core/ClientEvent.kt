package cc.warlock.warlock3.core

import cc.warlock.warlock3.core.compass.DirectionType

sealed class ClientEvent
object ClientDisconnectedEvent : ClientEvent()
data class ClientCommandEvent(val text: String) : ClientEvent()
data class ClientDataReceivedEvent(val text: String, val styles: List<WarlockStyle>, val stream: String?) : ClientEvent()
data class ClientOutputEvent(val text: StyledString, val stream: String? = null) : ClientEvent()
data class ClientEolEvent(val stream: String?) : ClientEvent()
data class ClientPropertyChangedEvent(val name: String, val value: String?) : ClientEvent()
data class ClientPromptEvent(val prompt: String) : ClientEvent()
data class ClientProgressBarEvent(val progressBarData: ProgressBarData) : ClientEvent()
data class ClientCompassEvent(val directions: List<DirectionType>) : ClientEvent()
data class ClientComponentUpdateEvent(val id: String, val text: StyledString) : ClientEvent()
