package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity

const val DEFAULT_MAX_TYPE_AHEAD = 0
const val MAX_TYPE_AHEAD_KEY = "typeahead"
const val SCRIPT_COMMAND_PREFIX_KEY = "script_command_prefix"
private const val MAIN_WINDOW_X_KEY = "mainWindowX"
private const val MAIN_WINDOW_Y_KEY = "mainWindowY"
private const val MAIN_WINDOW_WIDTH_KEY = "mainWindowWidth"
private const val MAIN_WINDOW_HEIGHT_KEY = "mainWindowHeight"

data class MainWindowBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

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

    suspend fun saveMainWindowBounds(
        characterId: String,
        bounds: MainWindowBounds,
    ) {
        save(characterId, MAIN_WINDOW_X_KEY, bounds.x.toString())
        save(characterId, MAIN_WINDOW_Y_KEY, bounds.y.toString())
        save(characterId, MAIN_WINDOW_WIDTH_KEY, bounds.width.toString())
        save(characterId, MAIN_WINDOW_HEIGHT_KEY, bounds.height.toString())
    }

    suspend fun getMainWindowBounds(characterId: String): MainWindowBounds? {
        val x = get(characterId, MAIN_WINDOW_X_KEY)?.toIntOrNull() ?: return null
        val y = get(characterId, MAIN_WINDOW_Y_KEY)?.toIntOrNull() ?: return null
        val width = get(characterId, MAIN_WINDOW_WIDTH_KEY)?.toIntOrNull() ?: return null
        val height = get(characterId, MAIN_WINDOW_HEIGHT_KEY)?.toIntOrNull() ?: return null

        return MainWindowBounds(
            x = x,
            y = y,
            width = width,
            height = height,
        )
    }

    fun observe(
        characterId: String,
        key: String,
    ): Flow<String?> = characterSettingsQueries.observeByKey(characterId = characterId, key = key)
}
