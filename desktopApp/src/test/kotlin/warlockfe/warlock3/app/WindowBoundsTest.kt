package warlockfe.warlock3.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
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
        x: Int = 0,
        y: Int = 0,
    ) = MainWindowBounds(x = x, y = y, width = width, height = height)

    /** Runs [saveWindowBounds] over [snapshots] for the duration of [block]. */
    private fun TestScope.saving(
        snapshots: MutableStateFlow<WindowSnapshot>,
        store: WindowBoundsStore,
        block: suspend TestScope.() -> Unit,
    ) {
        val job = launch { saveWindowBounds(snapshots, store) }
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
            val snapshots = MutableStateFlow(WindowSnapshot(null, bounds(800, 600)))
            saving(snapshots, store) {
                advanceTimeBy(justUnderDelay)
                snapshots.value = WindowSnapshot(null, bounds(900, 700))
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
            val snapshots = MutableStateFlow(WindowSnapshot("a", bounds(800, MIN_WINDOW_DIMENSION - 1)))
            saving(snapshots, store) {
                advanceTimeBy(pastDelay)
                assertEquals(listOf(800), store.widths)
                assertEquals(emptyList(), store.heights)
                assertEquals(emptyList(), store.characterSaves)
            }
        }

    @Test
    fun `saves bounds only for a connected character`() =
        runTest {
            val store = FakeStore()
            val snapshots = MutableStateFlow(WindowSnapshot(null, bounds(800, 600)))
            saving(snapshots, store) {
                advanceTimeBy(pastDelay)
                assertEquals(emptyList(), store.characterSaves)

                snapshots.value = WindowSnapshot("a", bounds(800, 600, x = 10, y = 20))
                advanceTimeBy(pastDelay)
                assertEquals(listOf("a" to bounds(800, 600, x = 10, y = 20)), store.characterSaves)
            }
        }

    @Test
    fun `bounds go to the character connected now, not the one before`() =
        runTest {
            val store = FakeStore()
            val snapshots = MutableStateFlow(WindowSnapshot("a", bounds(800, 600)))
            saving(snapshots, store) {
                advanceTimeBy(pastDelay)
                snapshots.value = WindowSnapshot("b", bounds(800, 600))
                advanceTimeBy(pastDelay)
                snapshots.value = WindowSnapshot("b", bounds(1000, 700))
                advanceTimeBy(pastDelay)

                assertEquals(bounds(800, 600), store.characterBounds["a"])
                assertEquals(bounds(1000, 700), store.characterBounds["b"])
            }
        }

    private fun placement(sizeFromCommandLine: Boolean) =
        GameWindowPlacement(
            size = DpSize(640.dp, 480.dp),
            position = WindowPosition.PlatformDefault,
            sizeFromCommandLine = sizeFromCommandLine,
        )

    @Test
    fun `restores a character's saved bounds`() =
        runTest {
            val store = FakeStore().apply { characterBounds["a"] = bounds(1000, 700, x = 5, y = 6) }
            assertEquals(
                bounds(1000, 700, x = 5, y = 6),
                characterBoundsToRestore("a", placement(sizeFromCommandLine = false), store),
            )
        }

    @Test
    fun `a size from the command line wins over the character's`() =
        runTest {
            val store = FakeStore().apply { characterBounds["a"] = bounds(1000, 700) }
            assertNull(characterBoundsToRestore("a", placement(sizeFromCommandLine = true), store))
        }

    @Test
    fun `restores nothing without a character or with unusable bounds`() =
        runTest {
            val store = FakeStore().apply { characterBounds["small"] = bounds(1000, MIN_WINDOW_DIMENSION - 1) }
            assertNull(characterBoundsToRestore(null, placement(sizeFromCommandLine = false), store))
            assertNull(characterBoundsToRestore("small", placement(sizeFromCommandLine = false), store))
            assertNull(characterBoundsToRestore("unknown", placement(sizeFromCommandLine = false), store))
        }
}
