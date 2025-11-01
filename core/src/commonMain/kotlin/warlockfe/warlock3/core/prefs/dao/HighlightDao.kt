package warlockfe.warlock3.core.prefs.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.HighlightEntity
import warlockfe.warlock3.core.prefs.models.HighlightStyleEntity
import warlockfe.warlock3.core.prefs.models.PopulatedHighlight
import kotlin.uuid.Uuid

@Dao
interface HighlightDao {
    @Transaction
    @Query("SELECT * FROM Highlight WHERE characterId = :characterId")
    fun observeHighlightsByCharacter(characterId: String): Flow<List<PopulatedHighlight>>

    @Transaction
    @Query("SELECT * FROM Highlight WHERE characterId = :characterId OR characterId = 'global'")
    fun observeHighlightsForCharacter(characterId: String): Flow<List<PopulatedHighlight>>

    @Query("DELETE FROM Highlight WHERE pattern = :pattern AND characterId = :characterId")
    suspend fun deleteByPattern(pattern: String, characterId: String)

    @Query("DELETE FROM Highlight WHERE id = :id")
    suspend fun deleteById(id: Uuid)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(highlight: HighlightEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(highlightStyleEntities: List<HighlightStyleEntity>)

    @Transaction
    suspend fun save(highlight: HighlightEntity, styles: List<HighlightStyleEntity>) {
        save(highlight)
        save(styles)
    }
}
