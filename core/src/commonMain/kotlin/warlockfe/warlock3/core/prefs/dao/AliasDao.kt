package warlockfe.warlock3.core.prefs.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.AliasEntity
import java.util.UUID

@Dao
interface AliasDao {
    @Query("SELECT * FROM Alias WHERE characterId = :characterId OR characterId = 'global'")
    fun observeByCharacterWithGlobals(characterId: String): Flow<List<AliasEntity>>

    @Query("SELECT * FROM Alias WHERE characterId = :characterId")
    fun observeByCharacter(characterId: String): Flow<List<AliasEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(alias: AliasEntity)

    @Query("DELETE FROM Alias WHERE id = :id")
    suspend fun delete(id: UUID)
}
