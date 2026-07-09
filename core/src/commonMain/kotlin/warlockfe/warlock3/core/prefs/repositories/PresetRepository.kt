package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.config.toPresetStyleConfig
import warlockfe.warlock3.core.prefs.config.toStyleDefinition
import warlockfe.warlock3.core.text.StyleDefinition

class PresetRepository(
    private val store: CharacterConfigStore,
) {
    // A single scope's saved presets (the character's own, or the shared "global" ones). The skin
    // defaults are merged in at the compose layer (WindowRegistryImpl). Used by the settings editor,
    // which shows/edits exactly one scope at a time.
    fun observePresetsForCharacter(characterId: String): Flow<Map<String, StyleDefinition>> =
        store.observe(characterId).map { config ->
            config.presets.mapValues { (_, style) -> style.toStyleDefinition() }
        }

    fun observeGlobal(): Flow<Map<String, StyleDefinition>> = observePresetsForCharacter(GLOBAL_CHARACTER_ID)

    // The presets that apply when rendering for [characterId]: the character's own presets layered over
    // the global (all-characters) presets, so a per-character override wins and anything unset falls back
    // to global. Global-scope resolution is just the global presets.
    fun observeForCharacter(characterId: String): Flow<Map<String, StyleDefinition>> =
        if (characterId == GLOBAL_CHARACTER_ID) {
            observePresetsForCharacter(characterId)
        } else {
            combine(store.observe(characterId), store.observe(GLOBAL_CHARACTER_ID)) { own, global ->
                (global.presets + own.presets).mapValues { (_, style) -> style.toStyleDefinition() }
            }
        }

    suspend fun save(
        characterId: String,
        key: String,
        style: StyleDefinition,
    ) {
        store.mutate(characterId) { current ->
            current.copy(presets = current.presets + (key to style.toPresetStyleConfig()))
        }
    }

    suspend fun saveGlobal(
        key: String,
        style: StyleDefinition,
    ) {
        save(GLOBAL_CHARACTER_ID, key, style)
    }
}
