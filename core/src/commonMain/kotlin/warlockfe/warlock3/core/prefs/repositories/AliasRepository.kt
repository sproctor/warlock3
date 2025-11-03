package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.AliasDao
import warlockfe.warlock3.core.prefs.models.AliasEntity
import warlockfe.warlock3.core.text.Alias
import kotlin.uuid.Uuid

class AliasRepository(
    private val aliasDao: AliasDao,
) {
    fun observeForCharacter(characterId: String): Flow<List<Alias>> {
        return aliasDao.observeByCharacterWithGlobals(characterId)
            .map { list ->
                list.mapNotNull {
                    try {
                        Alias(it.pattern, it.replacement)
                    } catch (_: Exception) {
                        null // TODO: Find a way to notify the user
                    }
                }
            }
    }

    fun observeByCharacter(characterId: String): Flow<List<AliasEntity>> {
        return aliasDao.observeByCharacter(characterId)
    }

    suspend fun save(alias: AliasEntity) {
        withContext(NonCancellable) {
            aliasDao.save(alias)
        }
    }

    suspend fun deleteById(id: Uuid) {
        withContext(NonCancellable) {
            aliasDao.delete(id)
        }
    }
}