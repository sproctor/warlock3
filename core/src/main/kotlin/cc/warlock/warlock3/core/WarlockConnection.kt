package cc.warlock.warlock3.core

interface WarlockConnection {
    fun disconnect()
    fun send(toSend: String)
    fun isConnected(): Boolean
}