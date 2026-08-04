package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.WindowLocation

/**
 * In-memory [WindowSettingsDao] for the geometry paths (docks and positions); the styling
 * methods are unused because styling lives in the character config, not the database.
 * [getByCharacter] mirrors the real query's ORDER BY position with NULLs first, but the order of
 * equal positions is arbitrary here just as SQLite leaves it unspecified, so tests must not
 * assert it. The @Transaction methods come from the interface's own implementations.
 */
class InMemoryWindowSettingsDao : WindowSettingsDao {
    private val rows = LinkedHashMap<Pair<String, String>, WindowSettingsEntity>()

    /** Number of individual position UPDATEs, for asserting that a clean layout writes nothing. */
    var positionWrites = 0
        private set

    override fun observeByCharacter(characterId: String): Flow<List<WindowSettingsEntity>> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<WindowSettingsEntity> =
        rows.values
            .filter { it.characterId == characterId }
            .sortedWith(compareBy { it.position })

    override suspend fun getByLocation(
        characterId: String,
        location: WindowLocation,
    ): List<WindowSettingsEntity> = rows.values.filter { it.characterId == characterId && it.location == location }

    override suspend fun getByName(
        characterId: String,
        name: String,
    ): WindowSettingsEntity? = rows[characterId to name]

    override suspend fun save(windowSettings: WindowSettingsEntity) {
        rows[windowSettings.characterId to windowSettings.name] = windowSettings
    }

    override suspend fun openWindow(
        characterId: String,
        name: String,
        location: WindowLocation,
        position: Int,
    ) {
        val existing = rows[characterId to name]
        rows[characterId to name] =
            existing?.copy(location = location, position = position, open = true)
                ?: windowSettingsEntity(
                    characterId = characterId,
                    name = name,
                    location = location,
                    position = position,
                    open = true,
                )
    }

    override suspend fun doCloseWindow(
        characterId: String,
        name: String,
    ) {
        rows[characterId to name]?.let { rows[characterId to name] = it.copy(open = false) }
    }

    override suspend fun setPosition(
        characterId: String,
        name: String,
        pos: Int,
    ) {
        positionWrites++
        rows[characterId to name]?.let { rows[characterId to name] = it.copy(position = pos) }
    }

    override suspend fun openGap(
        characterId: String,
        location: WindowLocation,
        position: Int,
    ) {
        shift(characterId, location, from = position, delta = 1)
    }

    override suspend fun closeGap(
        characterId: String,
        location: WindowLocation?,
        position: Int?,
    ) {
        if (location == null || position == null) return
        shift(characterId, location, from = position + 1, delta = -1)
    }

    private fun shift(
        characterId: String,
        location: WindowLocation,
        from: Int,
        delta: Int,
    ) {
        rows.entries.forEach { entry ->
            val row = entry.value
            val position = row.position
            if (row.characterId == characterId && row.location == location && row.open && position != null && position >= from) {
                entry.setValue(row.copy(position = position + delta))
            }
        }
    }

    override suspend fun deleteByCharacter(characterId: String) = error("unused")

    override suspend fun setStyle(
        characterId: String,
        name: String,
        textColor: WarlockColor,
        backgroundColor: WarlockColor,
        fontFamily: String?,
        fontSize: Float?,
        fontWeight: Int?,
    ) = error("unused")

    override suspend fun setNameFilter(
        characterId: String,
        name: String,
        nameFilter: Boolean,
    ) = error("unused")

    override suspend fun updateWidth(
        characterId: String,
        name: String,
        width: Int,
    ) = error("unused")

    override suspend fun updateHeight(
        characterId: String,
        name: String,
        height: Int,
    ) = error("unused")
}

/** A geometry-only row: the styling columns are unused defaults (styling lives in the config). */
fun windowSettingsEntity(
    characterId: String,
    name: String,
    location: WindowLocation? = null,
    position: Int? = null,
    open: Boolean = false,
): WindowSettingsEntity =
    WindowSettingsEntity(
        characterId = characterId,
        name = name,
        width = null,
        height = null,
        location = location,
        position = position,
        open = open,
        textColor = WarlockColor.Unspecified,
        backgroundColor = WarlockColor.Unspecified,
        fontFamily = null,
        fontSize = null,
        fontWeight = null,
    )

/**
 * The (name, position) pairs open at [location], in the order [WindowSettingsDao.getByCharacter]
 * returns them. Closed windows remember a placement there but are not docked.
 */
suspend fun WindowSettingsDao.dock(
    characterId: String,
    location: WindowLocation,
): List<Pair<String, Int?>> =
    getByCharacter(characterId)
        .filter { it.location == location && it.open }
        .map { it.name to it.position }
