package cc.warlock.warlock3.core

interface ClientListener {
    fun event(event: WarlockClient.ClientEvent)
}