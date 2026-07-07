package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity
import warlockfe.warlock3.core.text.FontConfig

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

/**
 * Per-character settings. The two user-editable scalars ([MAX_TYPE_AHEAD_KEY],
 * [SCRIPT_COMMAND_PREFIX_KEY]) live in the character's TOML config via [CharacterConfigStore]; the
 * generic key/value API routes everything else (main-window bounds, pane splits, ...) to SQLite,
 * since that's auto-saved geometry that churns on every resize.
 */
class CharacterSettingsRepository(
    private val characterSettingsQueries: CharacterSettingDao,
    private val store: CharacterConfigStore,
) {
    suspend fun save(
        characterId: String,
        key: String,
        value: String,
    ) {
        when (key) {
            MAX_TYPE_AHEAD_KEY -> {
                store.mutate(characterId) { it.copy(settings = it.settings.copy(typeahead = value.toIntOrNull())) }
            }

            SCRIPT_COMMAND_PREFIX_KEY -> {
                store.mutate(characterId) { it.copy(settings = it.settings.copy(scriptCommandPrefix = value)) }
            }

            else -> {
                withContext(NonCancellable) {
                    characterSettingsQueries.save(
                        CharacterSettingEntity(characterId = characterId, key = key, value = value),
                    )
                }
            }
        }
    }

    suspend fun get(
        characterId: String,
        key: String,
    ): String? =
        when (key) {
            MAX_TYPE_AHEAD_KEY -> {
                store
                    .current(characterId)
                    .settings.typeahead
                    ?.toString()
            }

            SCRIPT_COMMAND_PREFIX_KEY -> {
                store.current(characterId).settings.scriptCommandPrefix
            }

            else -> {
                characterSettingsQueries.getByKey(characterId = characterId, key = key)
            }
        }

    fun observe(
        characterId: String,
        key: String,
    ): Flow<String?> =
        when (key) {
            MAX_TYPE_AHEAD_KEY -> store.observe(characterId).map { it.settings.typeahead?.toString() }
            SCRIPT_COMMAND_PREFIX_KEY -> store.observe(characterId).map { it.settings.scriptCommandPrefix }
            else -> characterSettingsQueries.observeByKey(characterId = characterId, key = key)
        }

    // --- Default fonts: the character's TOML settings ---

    /** The font used for all normal (non-monospace) game text. Null until the user picks one. */
    fun observeDefaultFont(characterId: String): Flow<FontConfig?> = store.observe(characterId).map { it.settings.defaultFont }

    /** The font used for monospace-flagged text (ASCII maps, tables, etc.). Null until the user picks one. */
    fun observeMonoFont(characterId: String): Flow<FontConfig?> = store.observe(characterId).map { it.settings.monoFont }

    suspend fun saveDefaultFont(
        characterId: String,
        font: FontConfig?,
    ) {
        store.mutate(characterId) { it.copy(settings = it.settings.copy(defaultFont = font?.takeUnless { f -> f.isEmpty() })) }
    }

    suspend fun saveMonoFont(
        characterId: String,
        font: FontConfig?,
    ) {
        store.mutate(characterId) { it.copy(settings = it.settings.copy(monoFont = font?.takeUnless { f -> f.isEmpty() })) }
    }

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
}
