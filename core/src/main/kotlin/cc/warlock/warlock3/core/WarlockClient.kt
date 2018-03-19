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
        socket.getOutputStream().write(("<c>$toSend\n").toByteArray(Charsets.US_ASCII))
        notifyListeners(ClientDataSentEvent(toSend))
    }
    fun addListener(listener: ClientListener) {
        listeners.add(listener)
    }
    fun notifyListeners(event: ClientEvent) {
        listeners.forEach { it.event(event) }
    }
    interface ClientEvent
    class ClientDisconnectedEvent : ClientEvent
    class ClientDataSentEvent(val data: String) : ClientEvent
    class ClientDataReceivedEvent(val data: String) : ClientEvent
}