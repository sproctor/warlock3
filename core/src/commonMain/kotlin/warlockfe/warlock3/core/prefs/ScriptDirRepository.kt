package warlockfe.warlock3.core.prefs

import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.sql.ScriptDir
import warlockfe.warlock3.core.prefs.sql.ScriptDirQueries
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.core.util.mapToList

class ScriptDirRepository(
    private val scriptDirQueries: ScriptDirQueries,
    private val ioDispatcher: CoroutineDispatcher,
    private val warlockDirs: WarlockDirs,
) {
    fun observeScriptDirs(characterId: String): Flow<List<String>> {
        return scriptDirQueries.getByCharacter(characterId).asFlow().mapToList().flowOn(ioDispatcher)
    }

    fun observeGlobalScriptDirs(): Flow<List<String>> {
        return observeScriptDirs("global")
    }

    suspend fun getMappedScriptDirs(characterId: String): List<String> {
        return withContext(ioDispatcher) {
            scriptDirQueries.getByCharacterWithGlobal(characterId).executeAsList() + getDefaultDir()
        }
    }

    fun getDefaultDir(): String {
        return warlockDirs.dataDir + "/scripts/"
    }

    suspend fun save(characterId: String, path: String) {
        withContext(ioDispatcher) {
            scriptDirQueries.save(
                ScriptDir(
                    characterId = characterId, path = path
                )
            )
        }
    }

    suspend fun delete(characterId: String, path: String) {
        withContext(ioDispatcher) {
            scriptDirQueries.delete(
                characterId = characterId,
                path = path,
            )
        }
    }

    suspend fun saveGlobal(path: String) {
        save("global", path)
    }

    suspend fun deleteGlobal(path: String) {
        delete("global", path)
    }
}