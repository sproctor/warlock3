package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.AlterationDao
import warlockfe.warlock3.core.prefs.models.AlterationEntity
import kotlin.uuid.Uuid

class AlterationRepository(
    private val alterationDao: AlterationDao,
) {

    fun observeByCharacter(characterId: String): Flow<List<AlterationEntity>> {
        return alterationDao.observeAlterationsByCharacter(characterId = characterId)

    }

    fun observeForCharacter(characterId: String): Flow<List<AlterationEntity>> {
        return alterationDao.observeAlterationsByCharacterWithGlobals(
            characterId = characterId
        )
    }

    suspend fun save(alteration: AlterationEntity) {
        withContext(NonCancellable) {
            alterationDao.save(alteration)
        }
    }

    suspend fun deleteById(id: Uuid) {
        withContext(NonCancellable) {
            alterationDao.deleteById(id)
        }
    }
}