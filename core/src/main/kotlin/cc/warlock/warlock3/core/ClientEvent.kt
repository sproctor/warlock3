package cc.warlock.warlock3.core

sealed class ClientEvent {
    class ClientDisconnectedEvent : ClientEvent()
    class ClientDataSentEvent(val text: String) : ClientEvent()
    class ClientDataReceivedEvent(val text: StyledString) : ClientEvent()
}
