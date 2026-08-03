package warlockfe.warlock3.core.prefs.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.WindowLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * normalizePositions renumbers each dock to 0..n in position order. Releases up to 3.1.0-beta.27
 * recorded a game-opened window's position from a dock list the layout restore had not populated
 * yet, leaving duplicates behind, and duplicated positions sort by rowid - an order that need not
 * match the one the user last saw, so the layout shuffled between sessions.
 */
class WindowPositionNormalizationTest {
    private val character = "gs4:warlock"

    @Test
    fun duplicatePositionsAreRenumberedInSortOrder() =
        runBlocking {
            val dao = InMemoryDao()
            dao.openWindow(character, "thoughts", WindowLocation.RIGHT, 0)
            dao.openWindow(character, "logons", WindowLocation.RIGHT, 1)
            // A game-opened panel that raced the restore and recorded an already-taken position.
            dao.openWindow(character, "befriend", WindowLocation.RIGHT, 0)

            dao.normalizePositions(character)

            assertEquals(
                listOf("thoughts" to 0, "befriend" to 1, "logons" to 2),
                dao.dock(character, WindowLocation.RIGHT),
            )
        }

    @Test
    fun gapsAreCompactedAndNullPositionsPlacedFirst() =
        runBlocking {
            val dao = InMemoryDao()
            dao.openWindow(character, "deaths", WindowLocation.TOP, 2)
            dao.openWindow(character, "arrivals", WindowLocation.TOP, 5)
            dao.save(entity(name = "familiar", location = WindowLocation.TOP, position = null))

            dao.normalizePositions(character)

            assertEquals(
                listOf("familiar" to 0, "deaths" to 1, "arrivals" to 2),
                dao.dock(character, WindowLocation.TOP),
            )
        }

    @Test
    fun docksRenumberIndependently() =
        runBlocking {
            val dao = InMemoryDao()
            dao.openWindow(character, "thoughts", WindowLocation.LEFT, 3)
            dao.openWindow(character, "deaths", WindowLocation.RIGHT, 3)

            dao.normalizePositions(character)

            assertEquals(listOf("thoughts" to 0), dao.dock(character, WindowLocation.LEFT))
            assertEquals(listOf("deaths" to 0), dao.dock(character, WindowLocation.RIGHT))
        }

    @Test
    fun windowsOutOfTheLayoutAreLeftAlone() =
        runBlocking {
            val dao = InMemoryDao()
            dao.openWindow(character, "bank", WindowLocation.RIGHT, 4)
            dao.doCloseWindow(character, "bank")

            dao.normalizePositions(character)

            assertNull(dao.getByName(character, "bank")?.position)
            assertNull(dao.getByName(character, "bank")?.location)
        }

    @Test
    fun aCleanLayoutWritesNothing() =
        runBlocking {
            val dao = InMemoryDao()
            dao.openWindow(character, "thoughts", WindowLocation.RIGHT, 0)
            dao.openWindow(character, "logons", WindowLocation.RIGHT, 1)

            dao.normalizePositions(character)

            assertEquals(0, dao.positionWrites)
        }

    private fun entity(
        name: String,
        location: WindowLocation?,
        position: Int?,
    ) = WindowSettingsEntity(
        characterId = character,
        name = name,
        width = null,
        height = null,
        location = location,
        position = position,
        textColor = WarlockColor.Unspecified,
        backgroundColor = WarlockColor.Unspecified,
        fontFamily = null,
        fontSize = null,
        fontWeight = null,
    )
}

/**
 * Only what normalizePositions touches. [getByCharacter] mirrors the real query's ORDER BY
 * position: NULLs first, ties by insertion order (rowid).
 */
private class InMemoryDao : WindowSettingsDao {
    private val rows = LinkedHashMap<Pair<String, String>, WindowSettingsEntity>()
    var positionWrites = 0
        private set

    override suspend fun getByCharacter(characterId: String): List<WindowSettingsEntity> =
        rows.values
            .filter { it.characterId == characterId }
            .sortedWith(compareBy { it.position })

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
            existing?.copy(location = location, position = position)
                ?: WindowSettingsEntity(
                    characterId = characterId,
                    name = name,
                    width = null,
                    height = null,
                    location = location,
                    position = position,
                    textColor = WarlockColor.Unspecified,
                    backgroundColor = WarlockColor.Unspecified,
                    fontFamily = null,
                    fontSize = null,
                    fontWeight = null,
                )
    }

    override suspend fun setPosition(
        characterId: String,
        name: String,
        pos: Int,
    ) {
        positionWrites++
        rows[characterId to name]?.let { rows[characterId to name] = it.copy(position = pos) }
    }

    override suspend fun doCloseWindow(
        characterId: String,
        name: String,
    ) {
        rows[characterId to name]?.let { rows[characterId to name] = it.copy(location = null, position = null) }
    }

    override suspend fun getByName(
        characterId: String,
        name: String,
    ): WindowSettingsEntity? = rows[characterId to name]

    suspend fun dock(
        characterId: String,
        location: WindowLocation,
    ): List<Pair<String, Int?>> =
        getByCharacter(characterId)
            .filter { it.location == location }
            .map { it.name to it.position }

    override fun observeByCharacter(characterId: String): Flow<List<WindowSettingsEntity>> = error("unused")

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

    override suspend fun getByLocation(
        characterId: String,
        location: WindowLocation,
    ): List<WindowSettingsEntity> = error("unused")

    override suspend fun openGap(
        characterId: String,
        location: WindowLocation,
        position: Int,
    ) = error("unused")

    override suspend fun closeGap(
        characterId: String,
        location: WindowLocation?,
        position: Int?,
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
