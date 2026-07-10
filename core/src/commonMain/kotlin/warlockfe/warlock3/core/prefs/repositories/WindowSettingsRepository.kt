package warlockfe.warlock3.core.prefs.repositories

import co.touchlab.kermit.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.WindowStyleConfig
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.prefs.models.WindowSettings
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.WindowLocation

/**
 * Window settings, split across two stores: geometry (size/location/position) lives in SQLite via
 * [WindowSettingsDao] because it's auto-saved and churns on every resize, while styling (colors,
 * font, name filter) lives in the character's TOML config. [observeWindowSettings] merges the two by
 * window name; the geometry mutators hit the DAO and the styling mutators hit the config store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WindowSettingsRepository(
    private val windowSettingsDao: WindowSettingsDao,
    private val store: CharacterConfigStore,
) {
    private val logger = Logger.withTag("WindowSettingsRepository")

    fun observeWindowSettings(characterId: String): Flow<List<WindowSettings>> =
        combine(
            windowSettingsDao.observeByCharacter(characterId),
            store.observe(characterId),
        ) { geometryRows, config ->
            val geometryByName = geometryRows.associateBy { it.name }
            // Union of names so a window with only styling (or only geometry) isn't dropped.
            val names = geometryByName.keys + config.windows.keys
            names
                .map { name ->
                    val geometry = geometryByName[name]
                    val style = config.windows[name] ?: WindowStyleConfig()
                    WindowSettings(
                        characterId = characterId,
                        name = name,
                        width = geometry?.width,
                        height = geometry?.height,
                        location = geometry?.location,
                        position = geometry?.position,
                        textColor = style.textColor,
                        backgroundColor = style.backgroundColor,
                        font = style.font,
                        monoFont = style.monoFont,
                        nameFilter = style.nameFilter,
                        bold = style.bold,
                        italic = style.italic,
                        underline = style.underline,
                    )
                }
                // Match the DAO's `ORDER BY position` (SQLite sorts NULLs first on ascending).
                .sortedWith(compareBy { it.position })
        }

    suspend fun openWindow(
        characterId: String,
        name: String,
        location: WindowLocation,
        position: Int,
    ) {
        logger.d { "openWindow: $characterId, $name, $location, $position" }
        withContext(NonCancellable) {
            windowSettingsDao.openWindow(characterId, name, location, position)
        }
    }

    suspend fun closeWindow(
        characterId: String,
        name: String,
    ) {
        withContext(NonCancellable) {
            windowSettingsDao.closeWindow(characterId, name)
        }
    }

    suspend fun moveWindowToPosition(
        characterId: String,
        name: String,
        location: WindowLocation,
        position: Int,
    ) {
        withContext(NonCancellable) {
            windowSettingsDao.moveWindowToPosition(characterId, name, location, position)
        }
    }

    suspend fun setWindowWidth(
        characterId: String,
        name: String,
        width: Int,
    ) {
        withContext(NonCancellable) {
            windowSettingsDao.updateWidth(characterId = characterId, name = name, width = width)
        }
    }

    suspend fun setWindowHeight(
        characterId: String,
        name: String,
        height: Int,
    ) {
        withContext(NonCancellable) {
            windowSettingsDao.updateHeight(characterId = characterId, name = name, height = height)
        }
    }

    suspend fun setStyle(
        characterId: String,
        name: String,
        style: StyleDefinition,
    ) {
        store.mutate(characterId) { current ->
            val existing = current.windows[name] ?: WindowStyleConfig()
            val updated =
                existing.copy(
                    textColor = style.textColor,
                    backgroundColor = style.backgroundColor,
                    bold = style.bold,
                    italic = style.italic,
                    underline = style.underline,
                )
            current.copy(windows = current.windows + (name to updated))
        }
    }

    /** Sets the per-window normal-font override (null clears it, falling back to the character default). */
    suspend fun setFont(
        characterId: String,
        name: String,
        font: FontConfig?,
    ) {
        store.mutate(characterId) { current ->
            val existing = current.windows[name] ?: WindowStyleConfig()
            current.copy(windows = current.windows + (name to existing.copy(font = font?.takeUnless { it.isEmpty() })))
        }
    }

    /** Sets the per-window monospace-font override (null clears it, falling back to the character mono font). */
    suspend fun setMonoFont(
        characterId: String,
        name: String,
        monoFont: FontConfig?,
    ) {
        store.mutate(characterId) { current ->
            val existing = current.windows[name] ?: WindowStyleConfig()
            current.copy(windows = current.windows + (name to existing.copy(monoFont = monoFont?.takeUnless { it.isEmpty() })))
        }
    }

    suspend fun setNameFilter(
        characterId: String,
        name: String,
        nameFilter: Boolean,
    ) {
        store.mutate(characterId) { current ->
            val existing = current.windows[name] ?: WindowStyleConfig()
            current.copy(windows = current.windows + (name to existing.copy(nameFilter = nameFilter)))
        }
    }

    suspend fun setPosition(
        characterId: String,
        name: String,
        pos: Int,
    ) {
        withContext(NonCancellable) {
            logger.d { "setPosition: $characterId, $name, $pos" }
            windowSettingsDao.setPosition(characterId, name, pos)
        }
    }
}
