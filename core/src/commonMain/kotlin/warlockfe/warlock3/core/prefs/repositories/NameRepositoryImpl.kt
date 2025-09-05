package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.NameDao
import warlockfe.warlock3.core.prefs.models.NameEntity
import java.util.*

class NameRepositoryImpl(
    private val nameDao: NameDao,
): NameRepository {
    override fun observeGlobal(): Flow<List<NameEntity>> {
        return observeByCharacter("global")
    }

    override fun observeByCharacter(characterId: String): Flow<List<NameEntity>> {
        return nameDao.observeNamesByCharacter(characterId)
    }

    override fun observeForCharacter(characterId: String): Flow<List<NameEntity>> {
        return nameDao.observeNamesForCharacter(characterId)
    }

    override suspend fun save(name: NameEntity) {
        withContext(NonCancellable) {
            nameDao.save(name)
        }
    }

    override suspend fun deleteByText(characterId: String, text: String) {
        withContext(NonCancellable) {
            nameDao.deleteByText(text = text, characterId = characterId)
        }
    }

    override suspend fun deleteById(id: UUID) {
        withContext(NonCancellable) {
            nameDao.deleteById(id)
        }
    }
}
