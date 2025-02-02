package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.ConnectionSettingDao
import warlockfe.warlock3.core.prefs.models.ConnectionSettingEntity
import warlockfe.warlock3.core.sge.ConnectionProxySettings

class ConnectionSettingsRepository(
    private val connectionSettingDao: ConnectionSettingDao,
) {
    suspend fun save(connectionId: String, key: String, value: String) {
        withContext(NonCancellable) {
            connectionSettingDao.save(
                ConnectionSettingEntity(connectionId = connectionId, key = key, value = value)
            )
        }
    }

    suspend fun get(connectionId: String, key: String): String? {
        return connectionSettingDao.getByKey(connectionId = connectionId, key = key)
    }

    suspend fun getProxySettings(characterId: String): ConnectionProxySettings {
        return ConnectionProxySettings(
            enabled = get(characterId, "proxyEnabled")?.toBooleanStrictOrNull() == true,
            launchCommand = get(characterId, "proxyLaunchCommand"),
            host = get(characterId, "proxyHost"),
            port = get(characterId, "proxyPort"),
        )
    }

    suspend fun saveProxySettings(connectionId: String, proxySettings: ConnectionProxySettings) {
        withContext(NonCancellable) {
            save(
                connectionId = connectionId,
                key = "proxyEnabled",
                value = proxySettings.enabled.toString(),
            )
            if (proxySettings.launchCommand != null) {
                save(
                    connectionId = connectionId,
                    key = "proxyLaunchCommand",
                    value = proxySettings.launchCommand,
                )
            } else {
                connectionSettingDao.delete(
                    key = "proxyLaunchCommand",
                    connectionId = connectionId,
                )
            }
            if (proxySettings.host != null) {
                save(
                    connectionId = connectionId,
                    key = "proxyHost",
                    value = proxySettings.host,
                )
            } else {
                connectionSettingDao.delete(
                    key = "proxyHost",
                    connectionId = connectionId,
                )
            }
            if (proxySettings.port != null) {
                save(
                    connectionId = connectionId,
                    key = "proxyPort",
                    value = proxySettings.port,
                )
            } else {
                connectionSettingDao.delete(
                    key = "proxyPort",
                    connectionId = connectionId,
                )
            }
        }
    }
}
