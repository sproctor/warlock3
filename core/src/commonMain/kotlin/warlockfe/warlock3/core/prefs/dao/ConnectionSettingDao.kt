package warlockfe.warlock3.core.prefs.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.ConnectionSettingEntity

@Dao
interface ConnectionSettingDao {
    @Query("SELECT value FROM connection_setting WHERE `key` = :key AND connection_id = :connectionId")
    suspend fun getByKey(
        key: String,
        connectionId: String,
    ): String?

    @Query("SELECT value FROM connection_setting WHERE `key` = :key AND connection_id = :connectionId")
    fun observeByKey(
        key: String,
        connectionId: String,
    ): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(connectionSetting: ConnectionSettingEntity)

    @Query("DELETE FROM connection_setting WHERE `key` = :key AND connection_id = :connectionId")
    suspend fun delete(
        key: String,
        connectionId: String,
    )
}
