package cc.warlock.warlock3.core.prefs

import app.cash.sqldelight.coroutines.asFlow
import cc.warlock.warlock3.core.prefs.sql.Alias
import cc.warlock.warlock3.core.prefs.sql.AliasQueries
import cc.warlock.warlock3.core.util.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class AliasRepository(
    private val aliasQueries: AliasQueries,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun observeForCharacter(characterId: String): Flow<List<cc.warlock.warlock3.core.text.Alias>> {
        return aliasQueries.getForCharacter(characterId)
            .asFlow()
            .mapToList()
            .map { list ->
                list.map {
                    cc.warlock.warlock3.core.text.Alias(it.pattern, it.replacement)
                }
            }
            .flowOn(ioDispatcher)
    }

    fun observeByCharacter(characterId: String): Flow<List<Alias>> {
        return aliasQueries.getByCharacter(characterId)
            .asFlow()
            .mapToList()
            .flowOn(ioDispatcher)
    }

    fun observeGlobal(): Flow<List<Alias>> {
        return observeByCharacter("global")
    }

    suspend fun save(alias: Alias) {
        withContext(ioDispatcher) {
            aliasQueries.save(alias)
        }
    }

    suspend fun deleteById(id: UUID) {
        withContext(ioDispatcher) {
            aliasQueries.delete(id)
        }
    }
}