package warlockfe.warlock3.core.prefs.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.WindowLocation
import warlockfe.warlock3.core.window.WindowPlacement

@Dao
interface WindowSettingsDao {
    @Query("SELECT * FROM WindowSettings WHERE characterId = :characterId ORDER BY position")
    fun observeByCharacter(characterId: String): Flow<List<WindowSettingsEntity>>

    @Query("SELECT * FROM WindowSettings WHERE characterId = :characterId ORDER BY position")
    suspend fun getByCharacter(characterId: String): List<WindowSettingsEntity>

    @Query("DELETE FROM WindowSettings WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: String)

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
            fontSize,
            fontWeight
        )
        VALUES (
            :characterId,
            :name,
            :textColor,
            :backgroundColor,
            :fontFamily,
            :fontSize,
            :fontWeight
        )
        ON CONFLICT(characterId, name) DO
        UPDATE SET
            textColor = :textColor,
            backgroundColor = :backgroundColor,
            fontFamily = :fontFamily,
            fontSize = :fontSize,
            fontWeight = :fontWeight;
    """,
    )
    suspend fun setStyle(
        characterId: String,
        name: String,
        textColor: WarlockColor,
        backgroundColor: WarlockColor,
        fontFamily: String?,
        fontSize: Float?,
        fontWeight: Int?,
    )

    @Query(
        """
        INSERT INTO WindowSettings (characterId, name, nameFilter)
        VALUES (:characterId, :name, :nameFilter)
        ON CONFLICT(characterId, name) DO
        UPDATE SET
            nameFilter = :nameFilter;
    """,
    )
    suspend fun setNameFilter(
        characterId: String,
        name: String,
        nameFilter: Boolean,
    )

    @Query(
        """
        INSERT INTO WindowSettings (characterId, name, location, position, open)
        VALUES (:characterId, :name, :location, :position, 1)
        ON CONFLICT(characterId, name) DO
        UPDATE SET
            location = :location,
            position = :position,
            open = 1;
    """,
    )
    suspend fun openWindow(
        characterId: String,
        name: String,
        location: WindowLocation,
        position: Int,
    )

    /**
     * Leaves the layout but keeps location/position as the remembered placement, so the window
     * reopens where the user left it (see [reopenWindow]).
     */
    @Query(
        """
        UPDATE WindowSettings
        SET open = 0
        WHERE characterId = :characterId AND name = :name;
    """,
    )
    suspend fun doCloseWindow(
        characterId: String,
        name: String,
    )

    @Transaction
    suspend fun closeWindow(
        characterId: String,
        name: String,
    ) {
        getByName(characterId = characterId, name = name)
            ?.takeIf { it.open }
            ?.let { window ->
                doCloseWindow(
                    characterId = characterId,
                    name = name,
                )
                closeGap(
                    characterId = characterId,
                    location = window.location,
                    position = window.position,
                )
            }
    }

    /**
     * Reopens a window at its remembered placement: the same index it had in its dock when it
     * closed, clamped to the dock's current size. Returns where it was placed, or null when the
     * window has never been placed (the caller picks a default location and appends). Reopening
     * an already-open window just reports its placement.
     */
    @Transaction
    suspend fun reopenWindow(
        characterId: String,
        name: String,
    ): WindowPlacement? {
        val window = getByName(characterId = characterId, name = name) ?: return null
        val location = window.location ?: return null
        if (window.open) return WindowPlacement(location, window.position ?: 0)
        val openCount = getByLocation(characterId = characterId, location = location).count { it.open }
        val position = (window.position ?: openCount).coerceIn(0, openCount)
        openGap(characterId = characterId, location = location, position = position)
        openWindow(characterId = characterId, name = name, location = location, position = position)
        return WindowPlacement(location, position)
    }

    @Query("SELECT * FROM WindowSettings WHERE characterId = :characterId AND location = :location")
    suspend fun getByLocation(
        characterId: String,
        location: WindowLocation,
    ): List<WindowSettingsEntity>

    @Query("SELECT * FROM WindowSettings WHERE characterId = :characterId AND name = :name")
    suspend fun getByName(
        characterId: String,
        name: String,
    ): WindowSettingsEntity?

    @Query(
        """
        UPDATE WindowSettings
        SET position = position + 1
        WHERE characterId = :characterId AND location = :location AND position >= :position AND open = 1
    """,
    )
    suspend fun openGap(
        characterId: String,
        location: WindowLocation,
        position: Int,
    )

    @Transaction
    suspend fun moveWindowToPosition(
        characterId: String,
        name: String,
        location: WindowLocation,
        position: Int,
    ) {
        val oldWindow = getByName(characterId, name)
        if (oldWindow != null) {
            closeGap(characterId, oldWindow.location, oldWindow.position)
        }
        openGap(characterId, location, position)
        openWindow(characterId, name, location, position)
    }

    // The gap operations maintain the open windows' 0..n numbering; a closed window's remembered
    // position is frozen at its close-time index (reopenWindow clamps it), so they skip it.
    @Query(
        """
        UPDATE WindowSettings
        SET position = position - 1
        WHERE characterId = :characterId AND location = :location AND position > :position AND open = 1;
    """,
    )
    suspend fun closeGap(
        characterId: String,
        location: WindowLocation?,
        position: Int?,
    )

    @Query(
        """
        UPDATE WindowSettings
        SET width = :width
        WHERE characterId = :characterId AND name = :name;
    """,
    )
    suspend fun updateWidth(
        characterId: String,
        name: String,
        width: Int,
    )

    @Query(
        """
        UPDATE WindowSettings
        SET height = :height
        WHERE characterId = :characterId AND name = :name
    """,
    )
    suspend fun updateHeight(
        characterId: String,
        name: String,
        height: Int,
    )

    @Query(
        """
        UPDATE WindowSettings
        SET position = :pos
        WHERE characterId = :characterId AND name = :name;
    """,
    )
    suspend fun setPosition(
        characterId: String,
        name: String,
        pos: Int,
    )

    /**
     * Rewrites each dock's open-window positions to 0..n in their current sort order. Racy
     * writes have left duplicates and gaps behind, and SQLite leaves the relative order of
     * duplicated positions unspecified, so until they are renumbered the restored order need not
     * match the one the user last saw. Closed windows keep their remembered positions.
     */
    @Transaction
    suspend fun normalizePositions(characterId: String) {
        getByCharacter(characterId)
            .filter { it.open && it.location != null }
            .groupBy { it.location }
            .values
            .forEach { windows ->
                windows.forEachIndexed { index, window ->
                    if (window.position != index) {
                        setPosition(characterId = characterId, name = window.name, pos = index)
                    }
                }
            }
    }

    /**
     * Places a window at the end of a dock, with the position computed here rather than passed
     * in: positions derived from on-screen lists went stale under concurrency and counted
     * transient panels that have no row, which is how duplicate positions were written.
     */
    @Transaction
    suspend fun openWindowAtEnd(
        characterId: String,
        name: String,
        location: WindowLocation,
    ) {
        val position =
            getByLocation(characterId = characterId, location = location)
                .filter { it.open && it.name != name }
                .maxOfOrNull { it.position ?: -1 }
                ?.plus(1) ?: 0
        openWindow(characterId = characterId, name = name, location = location, position = position)
    }

    /**
     * Persists a dock's full order in one transaction, position = index in [names]. A reorder
     * written as independent per-row updates could interleave with another writer and leave
     * duplicate positions behind.
     */
    @Transaction
    suspend fun setPositions(
        characterId: String,
        names: List<String>,
    ) {
        names.forEachIndexed { index, name ->
            setPosition(characterId = characterId, name = name, pos = index)
        }
    }
}
