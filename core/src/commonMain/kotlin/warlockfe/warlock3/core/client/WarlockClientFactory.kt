package warlockfe.warlock3.core.client

import warlockfe.warlock3.core.window.WindowRegistry

interface WarlockClientFactory {
    fun createClient(
        windowRegistry: WindowRegistry,
        socket: WarlockSocket,
    ): WarlockClient
}
