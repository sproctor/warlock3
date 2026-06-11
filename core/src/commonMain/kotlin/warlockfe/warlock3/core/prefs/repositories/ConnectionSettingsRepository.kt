package warlockfe.warlock3.core.prefs.repositories

import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.config.ConnectionConfig
import warlockfe.warlock3.core.sge.ConnectionProxySettings

/**
 * Per-connection proxy settings. These used to live in the generic `connection_setting` key/value
 * table; they're now folded into the connection entry in `connections.toml` via [ClientConfigStore].
 */
class ConnectionSettingsRepository(
    private val store: ClientConfigStore,
) {
    suspend fun getProxySettings(connectionId: String): ConnectionProxySettings {
        val connection = store.currentConnections().connections.firstOrNull { it.id == connectionId }
        return ConnectionProxySettings(
            enabled = connection?.proxyEnabled == true,
            launchCommand = connection?.proxyLaunchCommand,
            host = connection?.proxyHost,
            port = connection?.proxyPort,
        )
    }

    suspend fun saveProxySettings(
        connectionId: String,
        proxySettings: ConnectionProxySettings,
    ) {
        store.mutateConnections { registry ->
            val existing = registry.connections.firstOrNull { it.id == connectionId } ?: ConnectionConfig(id = connectionId)
            val updated =
                existing.copy(
                    proxyEnabled = proxySettings.enabled,
                    proxyLaunchCommand = proxySettings.launchCommand,
                    proxyHost = proxySettings.host,
                    proxyPort = proxySettings.port,
                )
            registry.copy(connections = registry.connections.filterNot { it.id == connectionId } + updated)
        }
    }
}
