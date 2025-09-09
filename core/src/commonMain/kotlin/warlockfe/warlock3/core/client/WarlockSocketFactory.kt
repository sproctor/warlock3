package warlockfe.warlock3.core.client

interface WarlockSocketFactory {
    fun create(host: String, port: Int): WarlockSocket
}