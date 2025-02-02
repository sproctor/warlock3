package warlockfe.warlock3.core.prefs.mappers

import warlockfe.warlock3.core.prefs.models.ConnectionSettingEntity
import warlockfe.warlock3.core.prefs.models.ConnectionWithSettings
import warlockfe.warlock3.core.sge.ConnectionProxySettings
import warlockfe.warlock3.core.sge.StoredConnection

fun ConnectionWithSettings.toDomain(): StoredConnection {
    val settingsMap = settings.toMap()
    return StoredConnection(
        id = connection.id,
        name = connection.name,
        username = account.username,
        password = account.password,
        character = connection.character,
        code = connection.gameCode,
        proxySettings = ConnectionProxySettings(
            enabled = settingsMap["proxyEnabled"]?.toBooleanStrictOrNull() == true,
            launchCommand = settingsMap["proxyLaunchCommand"],
            host = settingsMap["proxyHost"],
            port = settingsMap["proxyPort"],
        )
    )
}

private fun List<ConnectionSettingEntity>.toMap(): Map<String, String> {
    return associate { it.key to it.value }
}