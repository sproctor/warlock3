package warlockfe.warlock3.core.prefs.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity

@Dao
interface WindowSettingsDao {
    @Query("SELECT * FROM WindowSettings WHERE characterId = :characterId ORDER BY name")
    fun observeByCharacter(characterId: String): Flow<List<WindowSettingsEntity>>

    @Query("SELECT * FROM WindowSettings WHERE characterId = :characterId ORDER BY name")
    suspend fun getByCharacter(characterId: String): List<WindowSettingsEntity>

    @Query("SELECT * FROM WindowSettings WHERE characterId = :characterId AND name = :name")
    suspend fun getByName(
        characterId: String,
        name: String,
    ): WindowSettingsEntity?

    @Query("DELETE FROM WindowSettings WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(windowSettings: WindowSettingsEntity)

    /**
     * Puts the window in the layout. Where it docks is the docking layout JSON's business; this
     * row only remembers that it is open.
     */
    @Query(
        """
        INSERT INTO WindowSettings (characterId, name, open)
        VALUES (:characterId, :name, 1)
        ON CONFLICT(characterId, name) DO
        UPDATE SET
            open = 1;
    """,
    )
    suspend fun openWindow(
        characterId: String,
        name: String,
    )

    @Query(
        """
        UPDATE WindowSettings
        SET open = 0
        WHERE characterId = :characterId AND name = :name;
    """,
    )
    suspend fun closeWindow(
        characterId: String,
        name: String,
    )
}
