package warlockfe.warlock3.core.client

import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.window.StreamRegistry

interface WarlockClientFactory {
    fun createStormFrontClient(
        credentials: SimuGameCredentials,
        windowRepository: WindowRepository,
        streamRegistry: StreamRegistry,
    ): WarlockClient
}
