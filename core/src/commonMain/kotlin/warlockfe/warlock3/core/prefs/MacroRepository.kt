package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.MacroDao
import warlockfe.warlock3.core.prefs.models.MacroEntity

class MacroRepository(
    val macroDao: MacroDao,
) {

    suspend fun getGlobalMacros(): List<MacroEntity> {
        return macroDao.getGlobals()
    }

    fun observeGlobalMacros(): Flow<List<Pair<String, String>>> {
        return macroDao
            .observeGlobals()
            .map { list -> list.map { Pair(it.key, it.value) } }
    }

    fun observeCharacterMacros(characterId: String): Flow<List<Pair<String, String>>> {
        assert(characterId != "global")
        return macroDao
            .observeByCharacterWithGlobals(characterId)
            .map { list ->
                list.map { Pair(it.key, it.value) }
            }
    }

    fun observeOnlyCharacterMacros(characterId: String): Flow<List<Pair<String, String>>> {
        assert(characterId != "global")
        return macroDao
            .observeByCharacter(characterId)
            .map { list ->
                list.map { Pair(it.key, it.value) }
            }
    }

    suspend fun delete(characterId: String, key: String) {
        assert(characterId != "global")
        withContext(NonCancellable) {
            macroDao.delete(characterId, key)
        }
    }

    suspend fun deleteGlobal(key: String) {
        withContext(NonCancellable) {
            macroDao.delete("global", key)
        }
    }

    suspend fun put(characterId: String, key: String, value: String) {
        assert(characterId != "global")
        withContext(NonCancellable) {
            macroDao.save(
                MacroEntity(characterId, key, value)
            )
        }
    }

    suspend fun putGlobal(key: String, value: String) {
        withContext(NonCancellable) {
            macroDao.save(
                MacroEntity("global", key, value)
            )
        }
    }

    suspend fun deleteAllGlobals() {
        withContext(NonCancellable) {
            macroDao.deleteAllGlobals()
        }
    }
}