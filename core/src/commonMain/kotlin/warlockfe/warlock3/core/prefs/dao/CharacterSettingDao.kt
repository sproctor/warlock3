package warlockfe.warlock3.core.prefs.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity

@Dao
interface CharacterSettingDao {
    @Query("SELECT value FROM CharacterSetting WHERE `key` = :key AND characterId = :characterId")
    suspend fun getByKey(key: String, characterId: String): String?

    @Query("SELECT value FROM CharacterSetting WHERE `key` = :key AND characterId = :characterId")
    fun observeByKey(key: String, characterId: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(characterSetting: CharacterSettingEntity)
}