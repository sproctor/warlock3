package warlockfe.warlock3.core.prefs.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.PresetStyleEntity

@Dao
interface PresetStyleDao {
    @Query("SELECT * FROM PresetStyle WHERE characterId = :characterId")
    fun observeByCharacter(characterId: String): Flow<List<PresetStyleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(presetStyle: PresetStyleEntity)
}
