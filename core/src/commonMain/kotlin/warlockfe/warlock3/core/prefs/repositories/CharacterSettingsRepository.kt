package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity

const val defaultMaxTypeAhead = 0
const val maxTypeAheadKey = "typeahead"
const val scriptCommandPrefixKey = "script_command_prefix"

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
}
