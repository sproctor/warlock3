package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.VariableDao
import warlockfe.warlock3.core.prefs.models.VariableEntity


class VariableRepository(
    private val variableDao: VariableDao,
) {
    fun observeCharacterVariables(characterId: String): Flow<List<VariableEntity>> {
        return variableDao.observeByCharacter(characterId)
    }

    suspend fun put(variable: VariableEntity) {
        withContext(NonCancellable) {
            variableDao.save(variable)
        }
    }

    suspend fun put(characterId: String, name: String, value: String) {
        put(VariableEntity(characterId, name, value))
    }

    suspend fun delete(characterId: String, name: String) {
        withContext(NonCancellable) {
            variableDao.delete(characterId, name)
        }
    }
}
