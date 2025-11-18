package warlockfe.warlock3.core.prefs.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.ConnectionEntity
import warlockfe.warlock3.core.prefs.models.ConnectionWithSettings

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connection WHERE name = :name")
    suspend fun getByName(name: String): ConnectionWithSettings?

    @Transaction
    @Query("SELECT * FROM connection")
    fun observeAllWithDetails(): Flow<List<ConnectionWithSettings>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(connection: ConnectionEntity)

    @Query("DELETE FROM connection WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE connection SET name = :newName WHERE name = :oldName")
    suspend fun rename(oldName: String, newName: String)
}