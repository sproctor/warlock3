package warlockfe.warlock3.core.prefs.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.NameEntity
import kotlin.uuid.Uuid

@Dao
interface NameDao {
    @Transaction
    @Query("SELECT * FROM Name WHERE characterId = :characterId")
    fun observeNamesByCharacter(characterId: String): Flow<List<NameEntity>>

    @Transaction
    @Query("SELECT * FROM Name WHERE characterId = :characterId")
    suspend fun getByCharacter(characterId: String): List<NameEntity>

    @Transaction
    @Query("SELECT * FROM Name WHERE characterId = :characterId OR characterId = 'global'")
    fun observeNamesForCharacter(characterId: String): Flow<List<NameEntity>>

    @Query("DELETE FROM Name WHERE text = :text AND characterId = :characterId")
    suspend fun deleteByText(
        text: String,
        characterId: String,
    )

    @Query("DELETE FROM Name WHERE id = :id")
    suspend fun deleteById(id: Uuid)

    @Query("DELETE FROM Name WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(name: NameEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(names: List<NameEntity>)
}
