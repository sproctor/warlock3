package warlockfe.warlock3.core.prefs.repositories

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
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.toWarlockColor

/**
 * Window settings, split across two stores: the open flag lives in SQLite via [WindowSettingsDao],
 * while styling (colors, font, name filter) lives in the character's TOML config.
 * [observeWindowSettings] merges the two by window name. Where an open window docks is not
 * recorded here at all: arrangement belongs to the docking layout JSON.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WindowSettingsRepository(
    private val windowSettingsDao: WindowSettingsDao,
    private val store: CharacterConfigStore,
) {
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
                        open = geometry?.open == true,
                        textColor = style.textColor,
                        backgroundColor = style.backgroundColor,
                        font = style.font,
                        monoFont = style.monoFont,
                        nameFilter = style.nameFilter,
                        hidden = style.hidden,
                        bold = style.bold,
                        italic = style.italic,
                        underline = style.underline,
                        weight = style.weight,
                        textColorRef = style.textColorRef,
                        backgroundColorRef = style.backgroundColorRef,
                    )
                }
                // Match the DAO's `ORDER BY name`, deterministically.
                .sortedBy { it.name }
        }

    /**
     * Whether the user has closed this window and not asked for it back, in which case the game must
     * not reopen it. Read from the config store's loaded state, which is populated at startup.
     */
    fun isHidden(
        characterId: String,
        name: String,
    ): Boolean = store.current(characterId).windows[name]?.hidden == true

    /**
     * The user closing a window: it leaves the layout and is marked hidden, so the game cannot
     * bring it back with an `openDialog`. Use [removeWindowFromLayout] when the game is the one
     * closing it.
     */
    suspend fun closeWindow(
        characterId: String,
        name: String,
    ) {
        withContext(NonCancellable) {
            windowSettingsDao.closeWindow(characterId, name)
            setHidden(characterId = characterId, name = name, hidden = true)
        }
    }

    /**
     * A window leaving the layout without the user asking, because the game closed the panel. It
     * is not marked hidden, so a later `openDialog` for it reopens it.
     */
    suspend fun removeWindowFromLayout(
        characterId: String,
        name: String,
    ) {
        withContext(NonCancellable) {
            windowSettingsDao.closeWindow(characterId, name)
        }
    }

    /**
     * Puts a window in the layout. Where it docks is the docking layout JSON's business (or, for a
     * window the JSON does not know, the game-announced location). Anything in the layout is by
     * definition not hidden, so this also clears the flag [closeWindow] sets.
     */
    suspend fun openWindow(
        characterId: String,
        name: String,
    ) {
        withContext(NonCancellable) {
            windowSettingsDao.openWindow(characterId = characterId, name = name)
            setHidden(characterId = characterId, name = name, hidden = false)
        }
    }

    private suspend fun setHidden(
        characterId: String,
        name: String,
        hidden: Boolean,
    ) {
        // Don't rewrite the character's config file when the flag already reads that way.
        if (isHidden(characterId = characterId, name = name) == hidden) return
        store.mutate(characterId) { current ->
            val existing = current.windows[name] ?: WindowStyleConfig()
            current.copy(windows = current.windows + (name to existing.copy(hidden = hidden)))
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
                    textColorRef = null,
                    backgroundColorRef = null,
                )
            current.copy(windows = current.windows + (name to updated))
        }
    }

    /** Persists a window's style as a sparse [StyleLayer], carrying the skin-palette refs (so they track the skin). */
    suspend fun setStyleLayer(
        characterId: String,
        name: String,
        layer: StyleLayer,
    ) {
        store.mutate(characterId) { current ->
            val existing = current.windows[name] ?: WindowStyleConfig()
            val updated =
                existing.copy(
                    textColor = layer.textColor ?: WarlockColor.Unspecified,
                    backgroundColor = layer.background.toWarlockColor(),
                    bold = layer.weight == 700,
                    italic = layer.italic,
                    underline = layer.underline,
                    weight = layer.weight?.takeUnless { it == 700 },
                    textColorRef = layer.textColorRef,
                    backgroundColorRef = layer.backgroundRef,
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
}
