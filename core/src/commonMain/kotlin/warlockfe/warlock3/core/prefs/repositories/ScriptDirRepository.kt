package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import warlockfe.warlock3.core.prefs.dao.ScriptDirDao
import warlockfe.warlock3.core.prefs.models.ScriptDirEntity
import warlockfe.warlock3.core.util.WarlockDirs

class ScriptDirRepository(
    private val scriptDirDao: ScriptDirDao,
    private val warlockDirs: WarlockDirs,
) {
    fun observeScriptDirs(characterId: String): Flow<List<String>> {
        return scriptDirDao.observeByCharacter(characterId)
    }

    suspend fun getMappedScriptDirs(characterId: String): List<Path> {
        return (scriptDirDao.getByCharacterWithGlobal(characterId) + getDefaultDir())
            .toSet().map { Path(it) }
    }

    fun getDefaultDir(): String {
        return Path(warlockDirs.dataDir, "scripts").toString()
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
