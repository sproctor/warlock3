package warlockfe.warlock3.core.prefs.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.CharacterEntity

@Dao
interface CharacterDao {
    @Query("SELECT * FROM Character WHERE id = :id")
    suspend fun getById(id: String): CharacterEntity?

    @Query("SELECT * FROM Character")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(character: CharacterEntity)

    @Query("DELETE FROM Character WHERE id = :id")
    suspend fun delete(id: String)
}
