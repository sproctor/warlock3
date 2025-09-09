package warlockfe.warlock3.core.client

import warlockfe.warlock3.core.prefs.repositories.WindowRepository
import warlockfe.warlock3.core.window.StreamRegistry

interface WarlockClientFactory {
    fun createClient(
        windowRepository: WindowRepository,
        streamRegistry: StreamRegistry,
        socket: WarlockSocket,
    ): WarlockClient
}
