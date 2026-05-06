package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity

const val DEFAULT_MAX_TYPE_AHEAD = 0
const val MAX_TYPE_AHEAD_KEY = "typeahead"
const val SCRIPT_COMMAND_PREFIX_KEY = "script_command_prefix"

class CharacterSettingsRepository(
    private val characterSettingsQueries: CharacterSettingDao,
) {
    suspend fun save(
        characterId: String,
        key: String,
        value: String,
    ) {
        withContext(NonCancellable) {
            characterSettingsQueries.save(
                CharacterSettingEntity(characterId = characterId, key = key, value = value),
            )
        }
    }

    suspend fun get(
        characterId: String,
        key: String,
    ): String? = characterSettingsQueries.getByKey(characterId = characterId, key = key)

    fun observe(
        characterId: String,
        key: String,
    ): Flow<String?> = characterSettingsQueries.observeByKey(characterId = characterId, key = key)
}
