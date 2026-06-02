package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.PresetStyleDao
import warlockfe.warlock3.core.prefs.mappers.toPresetStyleEntity
import warlockfe.warlock3.core.prefs.mappers.toStyleDefinition
import warlockfe.warlock3.core.text.StyleDefinition

class PresetRepository(
    private val presetStyleQueries: PresetStyleDao,
) {
    // Returns only the character's saved presets. The default styles now live in the skin's
    // "presets" section and are merged in at the compose layer (WindowRegistryImpl).
    fun observePresetsForCharacter(characterId: String): Flow<Map<String, StyleDefinition>> =
        presetStyleQueries
            .observeByCharacter(
                characterId = characterId,
            ).map { preset -> preset.associate { it.presetId to it.toStyleDefinition() } }

    suspend fun save(
        characterId: String,
        key: String,
        style: StyleDefinition,
    ) {
        withContext(NonCancellable) {
            presetStyleQueries.save(
                style.toPresetStyleEntity(
                    key = key,
                    characterId = characterId,
                ),
            )
        }
    }
}
