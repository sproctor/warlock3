package warlockfe.warlock3.core.prefs.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.PresetStyleEntity

@Dao
interface PresetStyleDao {
    @Query("SELECT * FROM PresetStyle WHERE characterId = :characterId")
    fun observeByCharacter(characterId: String): Flow<List<PresetStyleEntity>>

    @Query("SELECT * FROM PresetStyle WHERE characterId = :characterId")
    suspend fun getByCharacter(characterId: String): List<PresetStyleEntity>

    @Query("DELETE FROM PresetStyle WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(presetStyle: PresetStyleEntity)
}
