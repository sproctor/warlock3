package warlockfe.warlock3.core.prefs.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.VariableEntity

@Dao
interface VariableDao {
    @Query("SELECT * FROM Variable WHERE characterId = :characterId;")
    fun observeByCharacter(characterId: String): Flow<List<VariableEntity>>

    @Query("DELETE FROM Variable WHERE characterId = :characterId AND name = :name;")
    suspend fun delete(characterId: String, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(variable: VariableEntity)
}
