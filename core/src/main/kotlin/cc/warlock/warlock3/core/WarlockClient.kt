package cc.warlock.warlock3.core

import java.net.Socket

interface WarlockClient {
    var socket: Socket
    val listeners: MutableCollection<ClientListener>

    fun disconnect() {
        socket.close()
        notifyListeners(ClientDisconnectedEvent())
    }
    fun send(toSend: String) {
        if (!socket.isClosed) {
            socket.getOutputStream().write(toSend.toByteArray(Charsets.US_ASCII))
        }
        notifyListeners(ClientDataSentEvent(toSend))
    }
    fun addListener(listener: ClientListener) {
        listeners.add(listener)
    }
    fun notifyListeners(event: ClientEvent) {
        listeners.forEach { it.event(event) }
    }

    fun getClientViewListener(): ClientViewListener

    interface ClientEvent
    class ClientDisconnectedEvent : ClientEvent
    class ClientDataSentEvent(val text: String) : ClientEvent
    class ClientDataReceivedEvent(val text: StyledString) : ClientEvent

    interface ClientViewListener {
        fun commandEntered(command: String)
    }
}

