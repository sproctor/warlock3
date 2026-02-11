package warlockfe.warlock3.core.prefs.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.MacroEntity

@Dao
interface MacroDao {

    @Query("SELECT COUNT(*) FROM Macro WHERE characterId = 'global'")
    suspend fun getGlobalCount(): Int

    @Query("SELECT * FROM Macro WHERE `key` = ''")
    suspend fun getOldMacros(): List<MacroEntity>

    @Query("SELECT * FROM Macro WHERE characterId = 'global'")
    fun observeGlobals(): Flow<List<MacroEntity>>

    @Query("SELECT * FROM Macro WHERE characterId = :characterId")
    fun observeByCharacter(characterId: String): Flow<List<MacroEntity>>

    @Query("SELECT * FROM Macro WHERE characterId = :characterId")
    suspend fun getByCharacter(characterId: String): List<MacroEntity>

    @Query("SELECT * FROM Macro WHERE characterId = :characterId OR characterId = 'global'")
    fun observeByCharacterWithGlobals(characterId: String): Flow<List<MacroEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(macro: MacroEntity)

    @Query(
        """
        DELETE FROM Macro
        WHERE characterId = :characterId
            AND "key" = :keyString
    """
    )
    suspend fun delete(characterId: String, keyString: String)

    @Query("DELETE FROM Macro WHERE characterId = :characterId AND `key` = :key")
    suspend fun deleteByKey(characterId: String, key: String)

    @Query("DELETE FROM Macro WHERE characterId = 'global'")
    suspend fun deleteAllGlobals()
}