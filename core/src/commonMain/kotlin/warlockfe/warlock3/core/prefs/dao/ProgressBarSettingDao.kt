package warlockfe.warlock3.core.prefs.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.ProgressBarSettingEntity

@Dao
interface ProgressBarSettingDao {
    @Query("SELECT * FROM ProgressBarSetting WHERE characterId = :characterId;")
    fun observeByCharacter(characterId: String): Flow<List<ProgressBarSettingEntity>>

    // Used by the one-time TOML migration to read existing progress-bar styling.
    @Query("SELECT * FROM ProgressBarSetting WHERE characterId = :characterId;")
    suspend fun getByCharacter(characterId: String): List<ProgressBarSettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(setting: ProgressBarSettingEntity)

    @Query("DELETE FROM ProgressBarSetting WHERE characterId = :characterId AND id = :id;")
    suspend fun delete(
        characterId: String,
        id: String,
    )
}
