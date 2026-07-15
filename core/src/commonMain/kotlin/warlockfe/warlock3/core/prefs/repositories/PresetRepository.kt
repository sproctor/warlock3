package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.config.toPresetStyleConfig
import warlockfe.warlock3.core.prefs.config.toStyleDefinition
import warlockfe.warlock3.core.prefs.config.toStyleLayer
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.mergeLayers
import warlockfe.warlock3.core.text.resolve
import warlockfe.warlock3.core.text.toStyleDefinition

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
    // to global. Global-scope resolution is just the global presets. Merged per attribute (not per whole
    // preset), so a character-scope override of one attribute doesn't blank out an unrelated attribute
    // the global scope set for the same preset name.
    fun observeForCharacter(characterId: String): Flow<Map<String, StyleDefinition>> =
        if (characterId == GLOBAL_CHARACTER_ID) {
            observePresetsForCharacter(characterId)
        } else {
            combine(store.observe(characterId), store.observe(GLOBAL_CHARACTER_ID)) { own, global ->
                (global.presets.keys + own.presets.keys).associateWith { key ->
                    resolve(listOfNotNull(own.presets[key]?.toStyleLayer(), global.presets[key]?.toStyleLayer())).toStyleDefinition()
                }
            }
        }

    // Like [observeForCharacter] but as sparse [StyleLayer]s, so per-item fonts and the tri-state
    // background survive to the renderer. Used by the render pipeline (and the new appearance editor).
    // Merged per attribute (not per whole preset) so a character-scope override of one attribute (e.g.
    // italic) doesn't blank out the global scope's other attributes (e.g. color) for the same preset name.
    fun observeLayersForCharacter(characterId: String): Flow<Map<String, StyleLayer>> =
        if (characterId == GLOBAL_CHARACTER_ID) {
            store.observe(characterId).map { config -> config.presets.mapValues { (_, style) -> style.toStyleLayer() } }
        } else {
            combine(store.observe(characterId), store.observe(GLOBAL_CHARACTER_ID)) { own, global ->
                (global.presets.keys + own.presets.keys).associateWith { key ->
                    mergeLayers(listOfNotNull(own.presets[key]?.toStyleLayer(), global.presets[key]?.toStyleLayer()))
                }
            }
        }

    // Exactly one scope's own saved presets as sparse [StyleLayer]s (no merge, no skin) - what the
    // appearance editor edits, since it writes one scope at a time.
    fun observeScopeLayers(characterId: String): Flow<Map<String, StyleLayer>> =
        store.observe(characterId).map { config -> config.presets.mapValues { (_, style) -> style.toStyleLayer() } }

    suspend fun save(
        characterId: String,
        key: String,
        style: StyleDefinition,
    ) {
        store.mutate(characterId) { current ->
            current.copy(presets = current.presets + (key to style.toPresetStyleConfig()))
        }
    }

    // Persist a preset as a sparse [StyleLayer], so per-item fonts and the tri-state background survive.
    // Canonicalizes weight 700 back to bold on write (see [StyleLayer.toPresetStyleConfig]).
    suspend fun saveLayer(
        characterId: String,
        key: String,
        layer: StyleLayer,
    ) {
        store.mutate(characterId) { current ->
            current.copy(presets = current.presets + (key to layer.toPresetStyleConfig()))
        }
    }

    suspend fun saveGlobal(
        key: String,
        style: StyleDefinition,
    ) {
        save(GLOBAL_CHARACTER_ID, key, style)
    }
}
