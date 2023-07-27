package cc.warlock.warlock3.core.prefs

import app.cash.sqldelight.coroutines.asFlow
import cc.warlock.warlock3.core.prefs.sql.ScriptDir
import cc.warlock.warlock3.core.prefs.sql.ScriptDirQueries
import cc.warlock.warlock3.core.util.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class ScriptDirRepository(
    private val scriptDirQueries: ScriptDirQueries,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun observeScriptDirs(characterId: String): Flow<List<String>> {
        return scriptDirQueries.getByCharacter(characterId)
            .asFlow()
            .mapToList()
            .flowOn(ioDispatcher)
    }

    fun observeGlobalScriptDirs(): Flow<List<String>> {
        return observeScriptDirs("global")
    }

    suspend fun getMappedScriptDirs(characterId: String): List<String> {
        return withContext(ioDispatcher) {
            (listOf("%config%/scripts/") +
                    scriptDirQueries.getByCharacterWithGlobal(characterId)
                        .executeAsList()
                    ).map {
                    val home = System.getProperty("user.home")
                    val config = "$home/.warlock3"
                    it.replace(oldValue = "%home%", newValue = home, ignoreCase = true)
                        .replace(oldValue = "%config%", newValue = config, ignoreCase = true)
                }
        }
    }

    suspend fun save(characterId: String, path: String) {
        withContext(ioDispatcher) {
            scriptDirQueries.save(
                ScriptDir(
                    characterId = characterId,
                    path = path
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