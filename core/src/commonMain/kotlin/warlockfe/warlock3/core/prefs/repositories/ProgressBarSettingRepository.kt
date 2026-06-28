package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.ProgressBarConfig
import warlockfe.warlock3.core.prefs.models.ProgressBarSetting
import warlockfe.warlock3.core.text.WarlockColor

class ProgressBarSettingRepository(
    private val store: CharacterConfigStore,
) {
    fun observeByCharacter(characterId: String): Flow<List<ProgressBarSetting>> =
        store.observe(characterId).map { config ->
            config.progressBars.map { (id, setting) ->
                ProgressBarSetting(
                    characterId = characterId,
                    id = id,
                    barColor = setting.barColor,
                    backgroundColor = setting.backgroundColor,
                    textColor = setting.textColor,
                    fontFamily = setting.fontFamily,
                    fontSize = setting.fontSize,
                    fontWeight = setting.fontWeight,
                )
            }
        }

    // setColors and setFont each merge into the existing entry so changing one does not clear the
    // other (the config entry holds both colors and font).
    suspend fun setColors(
        characterId: String,
        id: String,
        barColor: WarlockColor,
        backgroundColor: WarlockColor,
        textColor: WarlockColor,
    ) {
        store.mutate(characterId) { current ->
            val existing = current.progressBars[id] ?: ProgressBarConfig()
            val updated =
                existing.copy(
                    barColor = barColor,
                    backgroundColor = backgroundColor,
                    textColor = textColor,
                )
            current.copy(progressBars = current.progressBars + (id to updated))
        }
    }

    suspend fun setFont(
        characterId: String,
        id: String,
        fontFamily: String?,
        fontSize: Float?,
        fontWeight: Int?,
    ) {
        store.mutate(characterId) { current ->
            val existing = current.progressBars[id] ?: ProgressBarConfig()
            val updated =
                existing.copy(
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                )
            current.copy(progressBars = current.progressBars + (id to updated))
        }
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
