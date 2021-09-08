package cc.warlock.warlock3.core

sealed class ClientEvent
object ClientDisconnectedEvent : ClientEvent()
data class ClientDataSentEvent(val text: String) : ClientEvent()
data class ClientDataReceivedEvent(val text: String, val styles: List<WarlockStyle>, val stream: String?) : ClientEvent()
data class ClientOutputEvent(val text: StyledString, val stream: String? = null) : ClientEvent()
data class ClientEolEvent(val stream: String?) : ClientEvent()
data class ClientPropertyChangedEvent(val name: String, val value: String?) : ClientEvent()
data class ClientPromptEvent(val prompt: String) : ClientEvent()
