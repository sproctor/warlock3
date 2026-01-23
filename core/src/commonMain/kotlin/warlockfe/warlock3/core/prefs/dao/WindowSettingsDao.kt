package warlockfe.warlock3.core.prefs.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.WindowLocation

@Dao
interface WindowSettingsDao {
    @Query("SELECT * FROM WindowSettings WHERE characterId = :characterId ORDER BY position")
    fun observeByCharacter(characterId: String): Flow<List<WindowSettingsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(windowSettings: WindowSettingsEntity)

    @Query(
        """
        INSERT INTO WindowSettings (
            characterId,
            name,
            textColor,
            backgroundColor,
            fontFamily,
            fontSize
        )
        VALUES (
            :characterId,
            :name,
            :textColor,
            :backgroundColor,
            :fontFamily,
            :fontSize
        )
        ON CONFLICT(characterId, name) DO
        UPDATE SET
            textColor = :textColor,
            backgroundColor = :backgroundColor,
            fontFamily = :fontFamily,
            fontSize = :fontSize;
    """
    )
    suspend fun setStyle(
        characterId: String,
        name: String,
        textColor: WarlockColor,
        backgroundColor: WarlockColor,
        fontFamily: String?,
        fontSize: Float?,
    )

    @Query(
        """
        INSERT INTO WindowSettings (characterId, name, location, position)
        VALUES (:characterId, :name, :location, :position)
        ON CONFLICT(characterId, name) DO
        UPDATE SET
            location = :location,
            position = :position;
    """
    )
    suspend fun openWindow(characterId: String, name: String, location: WindowLocation, position: Int)

    @Query(
        """
        UPDATE WindowSettings
        SET location = NULL, position = NULL
        WHERE characterId = :characterId AND name = :name;
    """
    )
    suspend fun doCloseWindow(characterId: String, name: String)

    @Transaction
    suspend fun closeWindow(characterId: String, name: String) {
        getByName(characterId = characterId, name = name)
            ?.let { window ->
                doCloseWindow(
                    characterId = characterId,
                    name = name,
                )
                closeGap(
                    characterId = characterId,
                    location = window.location,
                    position = window.position
                )
            }
    }

    @Transaction
    suspend fun moveWindow(characterId: String, name: String, location: WindowLocation) {
        val oldWindow = getByName(characterId = characterId, name = name) ?: return
        val newPosition = getByLocation(characterId = characterId, location = location).size
        openWindow(characterId, name, location = location, position = newPosition)
        closeGap(characterId, oldWindow.location, oldWindow.position)
    }

    @Query("SELECT * FROM WindowSettings WHERE characterId = :characterId AND location = :location")
    suspend fun getByLocation(characterId: String, location: WindowLocation): List<WindowSettingsEntity>

    @Query("SELECT * FROM WindowSettings WHERE characterId = :characterId AND name = :name")
    suspend fun getByName(characterId: String, name: String): WindowSettingsEntity?

    @Query(
        """
        UPDATE WindowSettings
        SET position = position - 1
        WHERE characterId = :characterId AND location = :location AND position > :position;
    """
    )
    suspend fun closeGap(characterId: String, location: WindowLocation?, position: Int?)

    @Query(
        """
        UPDATE WindowSettings
        SET width = :width
        WHERE characterId = :characterId AND name = :name;
    """
    )
    suspend fun updateWidth(characterId: String, name: String, width: Int)

    @Query(
        """
        UPDATE WindowSettings
        SET height = :height
        WHERE characterId = :characterId AND name = :name
    """
    )
    suspend fun updateHeight(characterId: String, name: String, height: Int)

    @Query(
        """
        UPDATE WindowSettings
        SET position =
        CASE
        WHEN position = :curpos THEN :newpos
        WHEN position = :newpos THEN :curpos
        ELSE position
        END
        WHERE characterId = :characterId AND location = :location;
    """
    )
    suspend fun switchPositions(characterId: String, location: WindowLocation, curpos: Int, newpos: Int)

    @Query(
        """
        UPDATE WindowSettings
        SET position = :pos
        WHERE characterId = :characterId AND name = :name;
    """
    )
    suspend fun setPosition(characterId: String, name: String, pos: Int)
}
