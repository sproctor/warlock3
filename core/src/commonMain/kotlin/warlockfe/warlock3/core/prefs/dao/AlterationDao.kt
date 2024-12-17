package warlockfe.warlock3.core.prefs.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.AlterationEntity
import java.util.UUID

@Dao
interface AlterationDao {
    @Query("SELECT * FROM Alteration WHERE characterId = :characterId")
    fun observeAlterationsByCharacter(characterId: String): Flow<List<AlterationEntity>>

    @Query("SELECT * FROM Alteration WHERE characterId = :characterId OR characterId = 'global'")
    fun observeAlterationsByCharacterWithGlobals(characterId: String): Flow<List<AlterationEntity>>

    @Query("DELETE FROM Alteration WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(alteration: AlterationEntity)
}
