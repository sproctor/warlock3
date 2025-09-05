package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.ScriptDirDao
import warlockfe.warlock3.core.prefs.models.ScriptDirEntity
import warlockfe.warlock3.core.util.WarlockDirs
import java.io.File

class ScriptDirRepository(
    private val scriptDirDao: ScriptDirDao,
    private val warlockDirs: WarlockDirs,
) {
    fun observeScriptDirs(characterId: String): Flow<List<String>> {
        return scriptDirDao.observeByCharacter(characterId)
    }

    suspend fun getMappedScriptDirs(characterId: String): List<String> {
        return (scriptDirDao.getByCharacterWithGlobal(characterId) + getDefaultDir())
            .toSet().toList()
    }

    fun getDefaultDir(): String {
        return File(warlockDirs.dataDir, "scripts").absolutePath
    }

    suspend fun save(characterId: String, path: String) {
        withContext(NonCancellable) {
            scriptDirDao.save(
                ScriptDirEntity(characterId = characterId, path = path)
            )
        }
    }

    suspend fun delete(characterId: String, path: String) {
        withContext(NonCancellable) {
            scriptDirDao.delete(
                characterId = characterId,
                path = path,
            )
        }
    }
}
