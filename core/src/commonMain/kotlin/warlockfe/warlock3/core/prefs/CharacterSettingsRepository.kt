package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.client.CharacterProxySettings
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity

const val defaultMaxScrollLines = 5_000
const val scrollbackKey = "scrollback"

const val defaultMaxTypeAhead = 2
const val maxTypeAheadKey = "typeahead"

class CharacterSettingsRepository(
    private val characterSettingsQueries: CharacterSettingDao,
) {
    suspend fun save(characterId: String, key: String, value: String) {
        withContext(NonCancellable) {
            characterSettingsQueries.save(
                CharacterSettingEntity(characterId = characterId, key = key, value = value)
            )
        }
    }

    suspend fun get(characterId: String, key: String): String? {
        return characterSettingsQueries.getByKey(characterId = characterId, key = key)
    }

    fun observe(characterId: String, key: String): Flow<String?> {
        return characterSettingsQueries.observeByKey(characterId = characterId, key = key)
    }

    suspend fun getProxySettings(characterId: String): CharacterProxySettings {
        return CharacterProxySettings(
            enabled = get(characterId, "proxyEnabled")?.toBooleanStrictOrNull() == true,
            launchCommand = get(characterId, "proxyLaunchCommand"),
            host = get(characterId, "proxyHost"),
            port = get(characterId, "proxyPort"),
            delay = get(characterId, "proxyDelay")?.toLongOrNull(),
        )
    }

    suspend fun saveProxySettings(characterId: String, proxySettings: CharacterProxySettings) {
        withContext(NonCancellable) {
            characterSettingsQueries.save(
                CharacterSettingEntity(
                    characterId = characterId,
                    key = "proxyEnabled",
                    value = proxySettings.enabled.toString(),
                )
            )
            if (proxySettings.launchCommand != null) {
                characterSettingsQueries.save(
                    CharacterSettingEntity(
                        characterId = characterId,
                        key = "proxyLaunchCommand",
                        value = proxySettings.launchCommand,
                    )
                )
            } else {
                characterSettingsQueries.delete(key = "proxyLaunchCommand", characterId = characterId)
            }
            if (proxySettings.host != null) {
                characterSettingsQueries.save(
                    CharacterSettingEntity(
                        characterId = characterId,
                        key = "proxyHost",
                        value = proxySettings.host,
                    )
                )
            } else {
                characterSettingsQueries.delete(key = "proxyHost", characterId = characterId)
            }
            if (proxySettings.port != null) {
                characterSettingsQueries.save(
                    CharacterSettingEntity(
                        characterId = characterId,
                        key = "proxyPort",
                        value = proxySettings.port,
                    )
                )
            } else {
                characterSettingsQueries.delete(key = "proxyPort", characterId = characterId)
            }
            if (proxySettings.delay != null) {
                characterSettingsQueries.save(
                    CharacterSettingEntity(
                        characterId = characterId,
                        key = "proxyDelay",
                        value = proxySettings.delay.toString(),
                    )
                )
            } else {
                characterSettingsQueries.delete(key = "proxyDelay", characterId = characterId)
            }
        }
    }
}
