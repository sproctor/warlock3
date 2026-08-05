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
     * Leaves the layout, keeping the window's slot in the dock's total order (see [reopenWindow]).
     * Nothing else moves: the remaining open windows' positions go sparse, which is fine - only
     * their order matters, and [normalizePositions] compacts at connect.
     */
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

    @Query(
        """
        UPDATE WindowSettings
        SET open = 1
        WHERE characterId = :characterId AND name = :name;
    """,
    )
    suspend fun markOpen(
        characterId: String,
        name: String,
    )

    /**
     * Reopens a window at the slot its close kept, so it comes back between the same neighbors -
     * and windows closed together reopen in their original relative order, whatever order they
     * come back in. Returns the dock and the window's rank among the dock's open windows (what a
     * dock list needs for inserting), or null when the window has never been placed and the
     * caller must pick a default. Reopening an already-open window just reports its placement.
     */
    @Transaction
    suspend fun reopenWindow(
        characterId: String,
        name: String,
    ): WindowPlacement? {
        val window = getByName(characterId = characterId, name = name) ?: return null
        // A remembered MAIN reads as never placed: MAIN is the main text window's slot, legacy
        // rows remember it (2022-era defaults wrote them), and reopening into it would replace
        // the main window - or crash a dock list that has no MAIN slot.
        val location = window.location?.takeUnless { it == WindowLocation.MAIN } ?: return null
        if (!window.open) markOpen(characterId = characterId, name = name)
        val rank =
            getByLocation(characterId = characterId, location = location)
                .count { it.open && it.name != name && compareValues(it.position, window.position) < 0 }
        return WindowPlacement(location, rank)
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

    // Shifts the whole dock order - closed windows included, so an insertion above a remembered
    // slot moves it along with its neighbors.
    @Query(
        """
        UPDATE WindowSettings
        SET position = position + 1
        WHERE characterId = :characterId AND location = :location AND position >= :position
    """,
    )
    suspend fun openGap(
        characterId: String,
        location: WindowLocation,
        position: Int,
    )

    /**
     * Moves a window into [location] at [index], its rank among the dock's open windows. The
     * rank maps to a slot in the dock's total order (before the open window currently holding
     * that rank, past everything when the rank is past the end). The old dock is left sparse.
     */
    @Transaction
    suspend fun moveWindowToPosition(
        characterId: String,
        name: String,
        location: WindowLocation,
        index: Int,
    ) {
        val dock = getByLocation(characterId = characterId, location = location).filter { it.name != name }
        val target =
            dock
                .filter { it.open }
                .sortedWith(compareBy { it.position })
                .getOrNull(index)
        val slot =
            if (target != null) {
                // A target row can carry a NULL slot (imports write them; they sort first, and
                // the connect heal numbers them later) - inserting before it means the top.
                target.position ?: 0
            } else {
                appendSlot(dock)
            }
        openGap(characterId = characterId, location = location, position = slot)
        openWindow(characterId = characterId, name = name, location = location, position = slot)
    }

    /** The slot after every one in use - remembered slots included, so an append cannot collide. */
    private fun appendSlot(dock: List<WindowSettingsEntity>): Int = dock.maxOfOrNull { it.position ?: -1 }?.plus(1) ?: 0

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
     * Rewrites each dock's positions - open and closed windows as one sequence - to 0..n in
     * their current sort order, preserving every window's slot. Racy writes have left duplicates
     * behind, and SQLite leaves the relative order of duplicated positions unspecified, so until
     * they are renumbered the restored order need not match the one the user last saw. This also
     * compacts the gaps that closes leave.
     */
    @Transaction
    suspend fun normalizePositions(characterId: String) {
        getByCharacter(characterId)
            .filter { it.location != null }
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
     * Places a window at the end of a dock - after every slot, remembered ones included, so it
     * cannot collide with one. The position is computed here rather than passed in: positions
     * derived from on-screen lists went stale under concurrency and counted transient panels
     * that have no row, which is how duplicate positions were written.
     */
    @Transaction
    suspend fun openWindowAtEnd(
        characterId: String,
        name: String,
        location: WindowLocation,
    ) {
        val position = appendSlot(getByLocation(characterId = characterId, location = location).filter { it.name != name })
        openWindow(characterId = characterId, name = name, location = location, position = position)
    }

    /**
     * Persists a dock reorder in one transaction: the open windows named in [names] take that
     * order, while every other row - closed windows, and open rows the caller does not know
     * about - keeps its slot from the top of the dock's full sequence. Keeping unnamed rows in
     * place (rather than appending them) means a row the screen no longer shows, like one whose
     * window later turned out non-resident, cannot drift on every reorder. One transaction
     * because a reorder written as independent per-row updates could interleave with another
     * writer and leave duplicate positions behind.
     */
    @Transaction
    suspend fun setPositions(
        characterId: String,
        location: WindowLocation,
        names: List<String>,
    ) {
        val dock = getByLocation(characterId = characterId, location = location).sortedWith(compareBy { it.position })
        val reordered = names.filter { name -> dock.any { it.open && it.name == name } }
        val newOrder = ArrayDeque(reordered)
        // Walk the dock's current sequence: each slot holding a reordered window takes the next
        // reordered name; every other row keeps its slot.
        val sequence = dock.map { row -> if (row.open && row.name in reordered) newOrder.removeFirst() else row.name }
        val positionsByName = dock.associate { it.name to it.position }
        sequence.forEachIndexed { index, name ->
            if (positionsByName[name] != index) {
                setPosition(characterId = characterId, name = name, pos = index)
            }
        }
    }
}
