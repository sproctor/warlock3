package warlockfe.warlock3.core.prefs.dao

import kotlinx.coroutines.runBlocking
import warlockfe.warlock3.core.prefs.InMemoryWindowSettingsDao
import warlockfe.warlock3.core.prefs.dock
import warlockfe.warlock3.core.prefs.windowSettingsEntity
import warlockfe.warlock3.core.window.WindowLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The dock-position invariants behind a stable window order across launches: normalizePositions
 * renumbers each dock to 0..n at connect (healing the duplicates racy writers left behind, which
 * SQLite would otherwise return in an unspecified order), openWindowAtEnd computes an appended
 * window's position inside the DB instead of trusting a caller-derived index, and setPositions
 * persists a reorder as one transaction.
 */
class WindowPositionsDaoTest {
    private val character = "gs4:warlock"

    @Test
    fun duplicatePositionsAreRenumberedToDistinctPositions() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "thoughts", WindowLocation.RIGHT, 0)
            dao.openWindow(character, "logons", WindowLocation.RIGHT, 1)
            // A game-opened panel that recorded an already-taken position (pre-fix data).
            dao.openWindow(character, "befriend", WindowLocation.RIGHT, 0)

            dao.normalizePositions(character)

            val dock = dao.dock(character, WindowLocation.RIGHT)
            // The tie between thoughts and befriend resolves in an unspecified order, so only
            // the invariants are asserted: positions become 0..n, and the untied window stays
            // after both previously tied ones.
            assertEquals(setOf(0, 1, 2), dock.map { it.second }.toSet())
            assertEquals(2, dock.first { it.first == "logons" }.second)
        }

    @Test
    fun gapsAreCompactedAndNullPositionsPlacedFirst() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "deaths", WindowLocation.TOP, 2)
            dao.openWindow(character, "arrivals", WindowLocation.TOP, 5)
            dao.save(windowSettingsEntity(character, "familiar", location = WindowLocation.TOP, position = null))

            dao.normalizePositions(character)

            assertEquals(
                listOf("familiar" to 0, "deaths" to 1, "arrivals" to 2),
                dao.dock(character, WindowLocation.TOP),
            )
        }

    @Test
    fun docksRenumberIndependently() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "thoughts", WindowLocation.LEFT, 3)
            dao.openWindow(character, "deaths", WindowLocation.RIGHT, 3)

            dao.normalizePositions(character)

            assertEquals(listOf("thoughts" to 0), dao.dock(character, WindowLocation.LEFT))
            assertEquals(listOf("deaths" to 0), dao.dock(character, WindowLocation.RIGHT))
        }

    @Test
    fun windowsOutOfTheLayoutAreLeftAlone() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "bank", WindowLocation.RIGHT, 4)
            dao.doCloseWindow(character, "bank")

            dao.normalizePositions(character)

            assertNull(dao.getByName(character, "bank")?.position)
            assertNull(dao.getByName(character, "bank")?.location)
        }

    @Test
    fun aCleanLayoutWritesNothing() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "thoughts", WindowLocation.RIGHT, 0)
            dao.openWindow(character, "logons", WindowLocation.RIGHT, 1)

            dao.normalizePositions(character)

            assertEquals(0, dao.positionWrites)
        }

    @Test
    fun openWindowAtEndAppendsAfterTheDocksSavedPositions() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "thoughts", WindowLocation.RIGHT, 0)
            dao.openWindow(character, "logons", WindowLocation.RIGHT, 1)
            dao.openWindow(character, "deaths", WindowLocation.TOP, 7)

            dao.openWindowAtEnd(character, "befriend", WindowLocation.RIGHT)

            // After this dock's windows, unaffected by the other dock's positions.
            assertEquals(2, dao.getByName(character, "befriend")?.position)
        }

    @Test
    fun openWindowAtEndStartsAnEmptyDockAtZero() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()

            dao.openWindowAtEnd(character, "befriend", WindowLocation.RIGHT)

            assertEquals(0, dao.getByName(character, "befriend")?.position)
        }

    @Test
    fun reopeningTheOnlyWindowInADockStaysAtZero() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "thoughts", WindowLocation.RIGHT, 0)

            dao.openWindowAtEnd(character, "thoughts", WindowLocation.RIGHT)

            assertEquals(0, dao.getByName(character, "thoughts")?.position)
        }

    @Test
    fun setPositionsWritesEachNameItsIndex() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "thoughts", WindowLocation.RIGHT, 0)
            dao.openWindow(character, "logons", WindowLocation.RIGHT, 1)
            dao.openWindow(character, "deaths", WindowLocation.RIGHT, 2)

            dao.setPositions(character, listOf("deaths", "thoughts", "logons"))

            assertEquals(
                listOf("deaths" to 0, "thoughts" to 1, "logons" to 2),
                dao.dock(character, WindowLocation.RIGHT),
            )
        }
}
