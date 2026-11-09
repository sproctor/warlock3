package warlockfe.warlock3.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import warlockfe.warlock3.core.prefs.repositories.MainWindowBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class WindowBoundsTest {
    private class FakeStore : WindowBoundsStore {
        val widths = mutableListOf<Int>()
        val heights = mutableListOf<Int>()
        val characterBounds = mutableMapOf<String, MainWindowBounds>()
        val characterSaves = mutableListOf<Pair<String, MainWindowBounds>>()

        override suspend fun putDefaultWidth(width: Int) {
            widths += width
        }

        override suspend fun putDefaultHeight(height: Int) {
            heights += height
        }

        override suspend fun getCharacterBounds(characterId: String) = characterBounds[characterId]

        override suspend fun putCharacterBounds(
            characterId: String,
            bounds: MainWindowBounds,
        ) {
            characterBounds[characterId] = bounds
            characterSaves += characterId to bounds
        }
    }

    private fun bounds(
        width: Int,
        height: Int,
        x: Int? = 0,
        y: Int? = 0,
    ) = MainWindowBounds(x = x, y = y, width = width, height = height)

    private fun snapshot(
        characterId: String?,
        bounds: MainWindowBounds,
        placement: WindowPlacement = WindowPlacement.Floating,
    ) = WindowSnapshot(characterId, bounds, placement)

    /** Runs [saveWindowBounds] over [snapshots] for the duration of [block]. */
    private fun TestScope.saving(
        snapshots: MutableStateFlow<WindowSnapshot>,
        store: WindowBoundsStore,
        openingSize: DpSize = DpSize(640.dp, 480.dp),
        block: suspend TestScope.() -> Unit,
    ) {
        val job = launch { saveWindowBounds(snapshots, store, openingSize) }
        runCurrent()
        launch {
            block()
            job.cancel()
        }
    }

    private val justUnderDelay = WINDOW_BOUNDS_SAVE_DELAY - 1.milliseconds
    private val pastDelay = WINDOW_BOUNDS_SAVE_DELAY + 1.milliseconds

    @Test
    fun `saves the size once the window holds still`() =
        runTest {
            val store = FakeStore()
            val snapshots = MutableStateFlow(snapshot(null, bounds(800, 600)))
            saving(snapshots, store) {
                advanceTimeBy(justUnderDelay)
                snapshots.value = snapshot(null, bounds(900, 700))
                advanceTimeBy(justUnderDelay)
                assertEquals(emptyList(), store.widths)

                advanceTimeBy(pastDelay)
                assertEquals(listOf(900), store.widths)
                assertEquals(listOf(700), store.heights)
            }
        }

    @Test
    fun `never saves a dimension below the minimum`() =
        runTest {
            val store = FakeStore()
            val snapshots = MutableStateFlow(snapshot("a", bounds(800, MIN_WINDOW_DIMENSION - 1)))
            saving(snapshots, store) {
                advanceTimeBy(pastDelay)
                assertEquals(listOf(800), store.widths)
                assertEquals(emptyList(), store.heights)
                assertEquals(emptyList(), store.characterSaves)
            }
        }

    @Test
    fun `does not save the size the window opened at until it changes`() =
        runTest {
            val store = FakeStore()
            val snapshots = MutableStateFlow(snapshot(null, bounds(300, 300)))
            saving(snapshots, store, openingSize = DpSize(300.dp, 300.dp)) {
                advanceTimeBy(pastDelay)
                assertEquals(emptyList(), store.widths)

                snapshots.value = snapshot(null, bounds(310, 300))
                advanceTimeBy(pastDelay)
                assertEquals(listOf(310), store.widths)
            }
        }

    @Test
    fun `saves nothing while maximized or fullscreen`() =
        runTest {
            val store = FakeStore()
            val snapshots = MutableStateFlow(snapshot("a", bounds(800, 600)))
            saving(snapshots, store) {
                advanceTimeBy(pastDelay)
                for (placement in listOf(WindowPlacement.Maximized, WindowPlacement.Fullscreen)) {
                    snapshots.value = snapshot("a", bounds(2560, 1440), placement)
                    advanceTimeBy(pastDelay)
                }
                assertEquals(listOf(800), store.widths)
                assertEquals(listOf("a" to bounds(800, 600)), store.characterSaves)
            }
        }

    @Test
    fun `saves bounds only for a connected character`() =
        runTest {
            val store = FakeStore()
            val snapshots = MutableStateFlow(snapshot(null, bounds(800, 600)))
            saving(snapshots, store) {
                advanceTimeBy(pastDelay)
                assertEquals(emptyList(), store.characterSaves)

                snapshots.value = snapshot("a", bounds(800, 600, x = 10, y = 20))
                advanceTimeBy(pastDelay)
                assertEquals(listOf("a" to bounds(800, 600, x = 10, y = 20)), store.characterSaves)
            }
        }

    @Test
    fun `saves a character's size while the window has no position`() =
        runTest {
            val store = FakeStore()
            val snapshots = MutableStateFlow(snapshot("a", bounds(800, 600, x = null, y = null)))
            saving(snapshots, store) {
                advanceTimeBy(pastDelay)
                assertEquals(listOf("a" to bounds(800, 600, x = null, y = null)), store.characterSaves)
            }
        }

    @Test
    fun `bounds go to the character connected now, not the one before`() =
        runTest {
            val store = FakeStore()
            val snapshots = MutableStateFlow(snapshot("a", bounds(800, 600)))
            saving(snapshots, store) {
                advanceTimeBy(pastDelay)
                snapshots.value = snapshot("b", bounds(800, 600))
                advanceTimeBy(pastDelay)
                snapshots.value = snapshot("b", bounds(1000, 700))
                advanceTimeBy(pastDelay)

                assertEquals(bounds(800, 600), store.characterBounds["a"])
                assertEquals(bounds(1000, 700), store.characterBounds["b"])
            }
        }

    @Test
    fun `a move alone does not save the size again`() =
        runTest {
            val store = FakeStore()
            val snapshots = MutableStateFlow(snapshot(null, bounds(800, 600)))
            saving(snapshots, store) {
                advanceTimeBy(pastDelay)
                snapshots.value = snapshot(null, bounds(800, 600, x = 50, y = 50))
                advanceTimeBy(pastDelay)
                assertEquals(listOf(800), store.widths)
                assertEquals(listOf(600), store.heights)
            }
        }

    @Test
    fun `a snapshot has a position only once the window has its own`() {
        val unplaced = WindowState(size = DpSize(800.dp, 600.dp), position = WindowPosition.PlatformDefault)
        assertEquals(
            snapshot("a", bounds(800, 600, x = null, y = null)),
            unplaced.snapshot("a"),
        )
        val placed =
            WindowState(
                placement = WindowPlacement.Maximized,
                size = DpSize(800.dp, 600.dp),
                position = WindowPosition(10.dp, 20.dp),
            )
        assertEquals(
            snapshot(null, bounds(800, 600, x = 10, y = 20), WindowPlacement.Maximized),
            placed.snapshot(null),
        )
    }

    private fun placement(
        widthFromCommandLine: Boolean = false,
        heightFromCommandLine: Boolean = false,
        positionFromCommandLine: Boolean = false,
    ) = GameWindowPlacement(
        size = DpSize(640.dp, 480.dp),
        position = WindowPosition.PlatformDefault,
        widthFromCommandLine = widthFromCommandLine,
        heightFromCommandLine = heightFromCommandLine,
        positionFromCommandLine = positionFromCommandLine,
    )

    @Test
    fun `restores a character's saved bounds`() =
        runTest {
            val store = FakeStore().apply { characterBounds["a"] = bounds(1000, 700, x = 5, y = 6) }
            assertEquals(
                RestoredBounds(DpSize(1000.dp, 700.dp), WindowPosition(5.dp, 6.dp)),
                characterBoundsToRestore("a", placement(), store),
            )
        }

    @Test
    fun `restores only the size when no position was saved`() =
        runTest {
            val store = FakeStore().apply { characterBounds["a"] = bounds(1000, 700, x = null, y = null) }
            assertEquals(
                RestoredBounds(DpSize(1000.dp, 700.dp), position = null),
                characterBoundsToRestore("a", placement(), store),
            )
        }

    @Test
    fun `a width, height or position from the command line wins over the character's`() =
        runTest {
            val store = FakeStore().apply { characterBounds["a"] = bounds(1000, 700, x = 5, y = 6) }
            assertEquals(
                RestoredBounds(DpSize(640.dp, 700.dp), WindowPosition(5.dp, 6.dp)),
                characterBoundsToRestore("a", placement(widthFromCommandLine = true), store),
            )
            assertEquals(
                RestoredBounds(DpSize(1000.dp, 480.dp), WindowPosition(5.dp, 6.dp)),
                characterBoundsToRestore("a", placement(heightFromCommandLine = true), store),
            )
            assertEquals(
                RestoredBounds(size = null, position = WindowPosition(5.dp, 6.dp)),
                characterBoundsToRestore(
                    "a",
                    placement(widthFromCommandLine = true, heightFromCommandLine = true),
                    store,
                ),
            )
            assertEquals(
                RestoredBounds(size = DpSize(1000.dp, 700.dp), position = null),
                characterBoundsToRestore("a", placement(positionFromCommandLine = true), store),
            )
            assertNull(
                characterBoundsToRestore(
                    "a",
                    placement(widthFromCommandLine = true, heightFromCommandLine = true, positionFromCommandLine = true),
                    store,
                ),
            )
        }

    @Test
    fun `restores nothing without a character or with unusable bounds`() =
        runTest {
            val store = FakeStore().apply { characterBounds["small"] = bounds(1000, MIN_WINDOW_DIMENSION - 1) }
            assertNull(characterBoundsToRestore(null, placement(), store))
            assertNull(characterBoundsToRestore("small", placement(), store))
            assertNull(characterBoundsToRestore("unknown", placement(), store))
        }

    @Test
    fun `a new window takes a normal opener's size`() {
        val opener = WindowState(placement = WindowPlacement.Floating, size = DpSize(900.dp, 700.dp))
        val placement = newWindowPlacement(opener, defaultSize = DpSize(640.dp, 480.dp))
        assertEquals(DpSize(900.dp, 700.dp), placement.size)
        assertEquals(WindowPosition.PlatformDefault, placement.position)
        assertFalse(placement.widthFromCommandLine)
        assertFalse(placement.heightFromCommandLine)
        assertFalse(placement.positionFromCommandLine)
    }

    @Test
    fun `a new window opened from a maximized or fullscreen one takes the default size`() {
        for (openerPlacement in listOf(WindowPlacement.Maximized, WindowPlacement.Fullscreen)) {
            val opener = WindowState(placement = openerPlacement, size = DpSize(2560.dp, 1440.dp))
            val placement = newWindowPlacement(opener, defaultSize = DpSize(640.dp, 480.dp))
            assertEquals(DpSize(640.dp, 480.dp), placement.size, "opened from $openerPlacement")
        }
    }
}
