package warlockfe.warlock3.core.prefs.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.ScriptDirEntity

@Dao
interface ScriptDirDao {
    @Query("SELECT path FROM ScriptDir WHERE characterId = :characterId ORDER BY path;")
    fun observeByCharacter(characterId: String): Flow<List<String>>

    @Query(
        """
            SELECT path FROM ScriptDir
            WHERE characterId = :characterId OR characterId = 'global'
            ORDER BY path
        """,
    )
    suspend fun getByCharacterWithGlobal(characterId: String): List<String>

    @Query("SELECT path FROM ScriptDir WHERE characterId = :characterId")
    suspend fun getByCharacter(characterId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun save(scriptDir: ScriptDirEntity)

    @Query("DELETE FROM ScriptDir WHERE characterId = :characterId AND path = :path")
    suspend fun delete(
        characterId: String,
        path: String,
    )

    @Query("DELETE FROM ScriptDir WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: String)
}
