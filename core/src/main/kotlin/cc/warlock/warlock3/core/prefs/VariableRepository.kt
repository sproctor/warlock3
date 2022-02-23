package cc.warlock.warlock3.core.prefs

import cc.warlock.warlock3.core.prefs.models.Variable
import cc.warlock.warlock3.core.prefs.sql.VariableQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import cc.warlock.warlock3.core.prefs.sql.Variable as DatabaseVariable


class VariableRepository(
    private val variableQueries: VariableQueries,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun observeCharacterVariables(characterId: String): Flow<List<Variable>> {
        return variableQueries.selectByCharacter(characterId)
            .asFlow()
            .mapToList(ioDispatcher)
            .map { dbVariables ->
                dbVariables.map { Variable(it.name, it.value_) }
            }
    }

    suspend fun put(characterId: String, variable: Variable) {
        withContext(ioDispatcher) {
            variableQueries.save(
                DatabaseVariable(
                    characterId = characterId,
                    name = variable.name,
                    value_ = variable.value,
                )
            )
        }
    }

    suspend fun put(characterId: String, name: String, value: String) {
        put(characterId, Variable(name, value))
    }

    suspend fun delete(characterId: String, name: String) {
        withContext(ioDispatcher) {
            variableQueries.delete(characterId, name)
        }
    }
}
