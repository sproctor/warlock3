package warlockfe.warlock3.core.prefs

import app.cash.sqldelight.coroutines.asFlow
import warlockfe.warlock3.core.prefs.sql.Macro
import warlockfe.warlock3.core.prefs.sql.MacroQueries
import warlockfe.warlock3.core.util.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MacroRepository(
    val macroQueries: MacroQueries,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun observeGlobalMacros(): Flow<List<Pair<String, String>>> {
        return macroQueries
            .getGlobals()
            .asFlow()
            .mapToList()
            .map { list -> list.map { Pair(it.key, it.value_) } }
            .flowOn(ioDispatcher)
    }

    fun observeCharacterMacros(characterId: String): Flow<List<Pair<String, String>>> {
        assert(characterId != "global")
        return macroQueries
            .getForCharacter(characterId)
            .asFlow()
            .mapToList()
            .map { list ->
                list.map { Pair(it.key, it.value_) }
            }
            .flowOn(ioDispatcher)
    }

    fun observeOnlyCharacterMacros(characterId: String): Flow<List<Pair<String, String>>> {
        assert(characterId != "global")
        return macroQueries
            .getByCharacter(characterId)
            .asFlow()
            .mapToList()
            .map { list ->
                list.map { Pair(it.key, it.value_) }
            }
            .flowOn(ioDispatcher)
    }

    suspend fun delete(characterId: String, key: String) {
        assert(characterId != "global")
        withContext(ioDispatcher) {
            macroQueries.delete(characterId, key)
        }
    }

    suspend fun deleteGlobal(key: String) {
        withContext(ioDispatcher) {
            macroQueries.delete("global", key)
        }
    }

    suspend fun put(characterId: String, key: String, value: String) {
        assert(characterId != "global")
        withContext(ioDispatcher) {
            macroQueries.save(
                Macro(characterId, key, value)
            )
        }
    }

    suspend fun putGlobal(key: String, value: String) {
        withContext(ioDispatcher) {
            macroQueries.save(
                Macro("global", key, value)
            )
        }
    }

    suspend fun deleteAllGlobals() {
        withContext(ioDispatcher) {
            macroQueries.deleteAllGlobals()
        }
    }
}