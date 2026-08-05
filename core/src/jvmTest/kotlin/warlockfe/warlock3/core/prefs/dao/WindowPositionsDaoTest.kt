package warlockfe.warlock3.core.prefs.dao

import kotlinx.coroutines.runBlocking
import warlockfe.warlock3.core.prefs.InMemoryWindowSettingsDao
import warlockfe.warlock3.core.prefs.dock
import warlockfe.warlock3.core.prefs.windowSettingsEntity
import warlockfe.warlock3.core.window.WindowLocation
import warlockfe.warlock3.core.window.WindowPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The dock-position invariants behind a stable window order across launches. A position is a
 * slot in the dock's total order, open and closed windows together: closing and reopening are
 * flag flips that keep the slot, so a window comes back between the same neighbors and windows
 * closed together reopen in their original relative order. normalizePositions renumbers each
 * dock's whole sequence to 0..n at connect (healing the duplicates racy writers left behind,
 * which SQLite would otherwise return in an unspecified order), openWindowAtEnd appends after
 * every slot with the position computed inside the DB, and setPositions persists a reorder as
 * one transaction with closed windows keeping their slot from the top.
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
            dao.save(windowSettingsEntity(character, "familiar", location = WindowLocation.TOP, position = null, open = true))

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
    fun aClosedWindowKeepsItsSlotThroughTheConnectHeal() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "thoughts", WindowLocation.RIGHT, 0)
            dao.openWindow(character, "bank", WindowLocation.RIGHT, 4)
            dao.openWindow(character, "logons", WindowLocation.RIGHT, 7)
            dao.closeWindow(character, "bank")

            dao.normalizePositions(character)

            // The whole sequence compacts to 0..n; bank stays out of the layout but keeps its
            // slot between its old neighbors.
            val row = dao.getByName(character, "bank")
            assertEquals(false, row?.open)
            assertEquals(WindowLocation.RIGHT, row?.location)
            assertEquals(1, row?.position)

            dao.reopenWindow(character, "bank")
            assertEquals(
                listOf("thoughts" to 0, "bank" to 1, "logons" to 2),
                dao.dock(character, WindowLocation.RIGHT),
            )
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
    fun aReopenedWindowReturnsToItsRememberedSpot() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "thoughts", WindowLocation.RIGHT, 0)
            dao.openWindow(character, "logons", WindowLocation.RIGHT, 1)
            dao.openWindow(character, "deaths", WindowLocation.RIGHT, 2)

            dao.closeWindow(character, "logons")
            assertEquals(listOf("thoughts" to 0, "deaths" to 2), dao.dock(character, WindowLocation.RIGHT))

            val placement = dao.reopenWindow(character, "logons")

            // The rank is 1: it goes back between its old neighbors in the dock list.
            assertEquals(WindowPlacement(WindowLocation.RIGHT, 1), placement)
            assertEquals(
                listOf("thoughts" to 0, "logons" to 1, "deaths" to 2),
                dao.dock(character, WindowLocation.RIGHT),
            )
        }

    @Test
    fun windowsClosedTogetherReopenInTheirOriginalOrder() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "thoughts", WindowLocation.RIGHT, 0)
            dao.openWindow(character, "logons", WindowLocation.RIGHT, 1)
            dao.openWindow(character, "deaths", WindowLocation.RIGHT, 2)
            dao.closeWindow(character, "logons")
            dao.closeWindow(character, "deaths")

            // Reopened in the opposite order they held - the kept slots still restore it.
            dao.reopenWindow(character, "deaths")
            dao.reopenWindow(character, "logons")

            assertEquals(
                listOf("thoughts" to 0, "logons" to 1, "deaths" to 2),
                dao.dock(character, WindowLocation.RIGHT),
            )
        }

    @Test
    fun reopeningANeverPlacedWindowReturnsNull() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            // A style-only row: settings were saved but the window was never docked.
            dao.save(windowSettingsEntity(character, "familiar"))

            assertNull(dao.reopenWindow(character, "familiar"))
            assertNull(dao.reopenWindow(character, "neverSeen"))
        }

    @Test
    fun aRememberedMainLocationReadsAsNeverPlaced() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            // Legacy rows remember MAIN (2022-era defaults wrote them); reopening into MAIN
            // would replace the main text window.
            dao.save(windowSettingsEntity(character, "spells", location = WindowLocation.MAIN, position = 0))

            assertNull(dao.reopenWindow(character, "spells"))
            assertEquals(false, dao.getByName(character, "spells")?.open)
        }

    @Test
    fun aNewWindowAppendsAfterRememberedSlots() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "thoughts", WindowLocation.RIGHT, 0)
            dao.openWindow(character, "deaths", WindowLocation.RIGHT, 5)
            dao.closeWindow(character, "deaths")

            dao.openWindowAtEnd(character, "befriend", WindowLocation.RIGHT)

            // Past deaths' remembered slot, so the append cannot collide with it and a later
            // reopen puts deaths back before befriend.
            assertEquals(6, dao.getByName(character, "befriend")?.position)
        }

    @Test
    fun setPositionsWritesEachNameItsIndex() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "thoughts", WindowLocation.RIGHT, 0)
            dao.openWindow(character, "logons", WindowLocation.RIGHT, 1)
            dao.openWindow(character, "deaths", WindowLocation.RIGHT, 2)

            dao.setPositions(character, WindowLocation.RIGHT, listOf("deaths", "thoughts", "logons"))

            assertEquals(
                listOf("deaths" to 0, "thoughts" to 1, "logons" to 2),
                dao.dock(character, WindowLocation.RIGHT),
            )
        }

    @Test
    fun aReorderLeavesUnnamedOpenRowsInTheirSlots() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "thoughts", WindowLocation.RIGHT, 0)
            // An open row the screen no longer shows (e.g. its window turned out non-resident):
            // it must not drift to the end on every reorder.
            dao.openWindow(character, "stale", WindowLocation.RIGHT, 1)
            dao.openWindow(character, "logons", WindowLocation.RIGHT, 2)

            dao.setPositions(character, WindowLocation.RIGHT, listOf("logons", "thoughts"))

            assertEquals(
                listOf("logons" to 0, "stale" to 1, "thoughts" to 2),
                dao.dock(character, WindowLocation.RIGHT),
            )
        }

    @Test
    fun aReorderKeepsAClosedWindowsSlotFromTheTop() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            dao.openWindow(character, "thoughts", WindowLocation.RIGHT, 0)
            dao.openWindow(character, "familiar", WindowLocation.RIGHT, 1)
            dao.openWindow(character, "logons", WindowLocation.RIGHT, 2)
            dao.openWindow(character, "deaths", WindowLocation.RIGHT, 3)
            dao.closeWindow(character, "familiar")

            dao.setPositions(character, WindowLocation.RIGHT, listOf("deaths", "thoughts", "logons"))

            // The open windows take the new order; familiar still holds the second slot.
            assertEquals(
                listOf("deaths" to 0, "thoughts" to 2, "logons" to 3),
                dao.dock(character, WindowLocation.RIGHT),
            )
            assertEquals(1, dao.getByName(character, "familiar")?.position)
            assertEquals(WindowPlacement(WindowLocation.RIGHT, 1), dao.reopenWindow(character, "familiar"))
        }
}
