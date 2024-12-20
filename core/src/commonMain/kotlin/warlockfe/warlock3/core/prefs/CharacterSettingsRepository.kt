package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity

const val defaultMaxScrollLines = 5_000
const val scrollbackKey = "scrollback"

const val defaultMaxTypeAhead = 1
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
}
