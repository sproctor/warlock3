package warlockfe.warlock3.core.client

import warlockfe.warlock3.core.sge.SimuGameCredentials

interface WarlockClientFactory {
    fun createStormFrontClient(credentials: SimuGameCredentials): WarlockClient
}
