package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.ProgressBarSettingDao
import warlockfe.warlock3.core.prefs.models.ProgressBarSettingEntity
import warlockfe.warlock3.core.text.WarlockColor

class ProgressBarSettingRepository(
    private val progressBarSettingDao: ProgressBarSettingDao,
) {
    fun observeByCharacter(characterId: String): Flow<List<ProgressBarSettingEntity>> =
        progressBarSettingDao.observeByCharacter(characterId)

    suspend fun save(setting: ProgressBarSettingEntity) {
        withContext(NonCancellable) {
            progressBarSettingDao.save(setting)
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
        withContext(NonCancellable) {
            progressBarSettingDao.delete(characterId, id)
        }
    }
}
