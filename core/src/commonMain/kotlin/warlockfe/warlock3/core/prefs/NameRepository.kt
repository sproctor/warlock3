package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.NameDao
import warlockfe.warlock3.core.prefs.models.NameEntity
import java.util.*

class NameRepository(
    private val nameDao: NameDao,
) {
    fun observeGlobal(): Flow<List<NameEntity>> {
        return observeByCharacter("global")
    }

    fun observeByCharacter(characterId: String): Flow<List<NameEntity>> {
        return nameDao.observeNamesByCharacter(characterId)
    }

    fun observeForCharacter(characterId: String): Flow<List<NameEntity>> {
        return nameDao.observeNamesForCharacter(characterId)
    }

    suspend fun save(name: NameEntity) {
        withContext(NonCancellable) {
            nameDao.save(name)
        }
    }

    suspend fun deleteByText(characterId: String, text: String) {
        withContext(NonCancellable) {
            nameDao.deleteByText(text = text, characterId = characterId)
        }
    }

    suspend fun deleteById(id: UUID) {
        withContext(NonCancellable) {
            nameDao.deleteById(id)
        }
    }
}
