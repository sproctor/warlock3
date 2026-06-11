package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.toConfig
import warlockfe.warlock3.core.prefs.config.toEntity
import warlockfe.warlock3.core.prefs.models.ProgressBarSettingEntity
import warlockfe.warlock3.core.text.WarlockColor

class ProgressBarSettingRepository(
    private val store: CharacterConfigStore,
) {
    fun observeByCharacter(characterId: String): Flow<List<ProgressBarSettingEntity>> =
        store.observe(characterId).map { config ->
            config.progressBars.map { (id, setting) -> setting.toEntity(characterId = characterId, id = id) }
        }

    suspend fun save(setting: ProgressBarSettingEntity) {
        store.mutate(setting.characterId) { current ->
            current.copy(progressBars = current.progressBars + (setting.id to setting.toConfig()))
        }
    }

    suspend fun setColors(
        characterId: String,
        id: String,
        barColor: WarlockColor,
        backgroundColor: WarlockColor,
        textColor: WarlockColor,
    ) {
        save(
            ProgressBarSettingEntity(
                characterId = characterId,
                id = id,
                barColor = barColor,
                backgroundColor = backgroundColor,
                textColor = textColor,
            ),
        )
    }

    suspend fun delete(
        characterId: String,
        id: String,
    ) {
        store.mutate(characterId) { current ->
            current.copy(progressBars = current.progressBars - id)
        }
    }
}
