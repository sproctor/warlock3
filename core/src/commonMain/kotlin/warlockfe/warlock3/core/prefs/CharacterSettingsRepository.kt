package warlockfe.warlock3.core.prefs

import app.cash.sqldelight.coroutines.asFlow
import warlockfe.warlock3.core.prefs.sql.CharacterSetting
import warlockfe.warlock3.core.prefs.sql.CharacterSettingQueries
import warlockfe.warlock3.core.util.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

const val defaultMaxScrollLines = 5_000
const val scrollbackKey = "scrollback"

class CharacterSettingsRepository(
    private val characterSettingsQueries: CharacterSettingQueries,
    private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun save(characterId: String, key: String, value: String) {
        withContext(ioDispatcher) {
            characterSettingsQueries.save(
                CharacterSetting(characterId = characterId, key = key, value_ = value)
            )
        }
    }

    suspend fun get(characterId: String, key: String): String? {
        return withContext(ioDispatcher) {
            characterSettingsQueries.getByKey(characterId = characterId, key = key).executeAsOneOrNull()
        }
    }

    fun observe(characterId: String, key: String): Flow<String?> {
        return characterSettingsQueries.getByKey(characterId = characterId, key = key)
            .asFlow()
            .mapToOneOrNull()
            .flowOn(ioDispatcher)
    }
}