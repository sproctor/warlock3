package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.toPresetStyleConfig
import warlockfe.warlock3.core.prefs.config.toStyleDefinition
import warlockfe.warlock3.core.text.StyleDefinition

class PresetRepository(
    private val store: CharacterConfigStore,
) {
    // Returns only the character's saved presets. The default styles now live in the skin's
    // "presets" section and are merged in at the compose layer (WindowRegistryImpl).
    fun observePresetsForCharacter(characterId: String): Flow<Map<String, StyleDefinition>> =
        store.observe(characterId).map { config ->
            config.presets.mapValues { (_, style) -> style.toStyleDefinition() }
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
}
