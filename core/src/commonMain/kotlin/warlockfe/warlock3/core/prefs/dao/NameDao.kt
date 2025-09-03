package warlockfe.warlock3.core.prefs.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.NameEntity
import warlockfe.warlock3.core.prefs.models.PopulatedHighlight
import java.util.*

@Dao
interface NameDao {
    @Transaction
    @Query("SELECT * FROM Name WHERE characterId = :characterId")
    fun observeNamesByCharacter(characterId: String): Flow<List<NameEntity>>

    @Transaction
    @Query("SELECT * FROM Name WHERE characterId = :characterId OR characterId = 'global'")
    fun observeNamesForCharacter(characterId: String): Flow<List<NameEntity>>

    @Query("DELETE FROM Name WHERE text = :text AND characterId = :characterId")
    suspend fun deleteByText(text: String, characterId: String)

    @Query("DELETE FROM Name WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(name: NameEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(names: List<NameEntity>)
}
