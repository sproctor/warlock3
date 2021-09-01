package cc.warlock.warlock3.core

sealed class ClientEvent
object ClientDisconnectedEvent : ClientEvent()
data class ClientDataSentEvent(val text: String) : ClientEvent()
data class ClientDataReceivedEvent(val text: String) : ClientEvent()
data class ClientOutputEvent(val text: StyledString) : ClientEvent()
object ClientEolEvent : ClientEvent()
data class ClientPropertyChangedEvent(val name: String, val value: String) : ClientEvent()
data class ClientStreamChangedEvent(val stream: String?) : ClientEvent()
data class ClientOutputStyleEvent(val style: WarlockStyle?) : ClientEvent()
data class ClientPromptEvent(val prompt: String) : ClientEvent()
data class ClientAddStyleEvent(val style: WarlockStyle) : ClientEvent()
data class ClientRemoveStyleEvent(val style: WarlockStyle) : ClientEvent()
object ClientClearStyleEvent : ClientEvent()