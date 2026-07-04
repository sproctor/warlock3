package warlockfe.warlock3.core.prefs.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.ClientSettingEntity

@Dao
interface ClientSettingDao {
    @Query("SELECT * FROM clientsetting")
    suspend fun getAll(): List<ClientSettingEntity>

    @Query("SELECT value FROM ClientSetting WHERE `key` = :key")
    suspend fun getByKey(key: String): String?

    @Query("SELECT value FROM ClientSetting WHERE `key` = :key")
    fun observeByKey(key: String): Flow<String?>

    @Query("DELETE FROM ClientSetting WHERE `key` = :key")
    suspend fun removeByKey(key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: ClientSettingEntity)
}
