package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import warlockfe.warlock3.compose.ui.window.ComposeTextStream
import warlockfe.warlock3.compose.ui.window.StreamWorkQueue
import warlockfe.warlock3.compose.util.HighlightIndex
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.util.SoundPlayer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowFinderTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workQueue = StreamWorkQueue(scope)

    private val stream =
        ComposeTextStream(
            id = MAIN_WINDOW_NAME,
            maxLines = 1000,
            markLinks = false,
            showImages = false,
            showTimestamps = false,
            suppressPrompts = false,
            highlights = MutableStateFlow(HighlightIndex(emptyList())),
            names = MutableStateFlow(emptyList()),
            ignores = MutableStateFlow(emptyList()),
            alterations = MutableStateFlow(emptyList()),
            presets = MutableStateFlow(emptyMap()),
            monoFont = MutableStateFlow(null),
            soundPlayer =
                object : SoundPlayer {
                    override suspend fun playSound(filename: String): String? = null
                },
            workQueue = workQueue,
            scope = scope,
        )

    // Only the main window has a stream; any other name is a panel.
    private val finder = WindowFinder { name -> stream.takeIf { name == MAIN_WINDOW_NAME } }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun lines(vararg texts: String) =
        runBlocking {
            texts.forEach { stream.appendLine(StyledString(it), ignoreWhenBlank = false, showWhenClosed = null) }
            workQueue.awaitFlushed()
        }

    private fun serialOf(index: Int): Long = stream.lines.value[index].serialNumber

    @Test
    fun opensOverTheSelectedWindowWithNothingMatched() {
        lines("an orc")
        finder.nextOrOpen(MAIN_WINDOW_NAME)

        val state = finder.state.value!!
        assertEquals(MAIN_WINDOW_NAME, state.windowName)
        assertEquals(0, state.totalMatches)
        assertEquals(0, state.currentNumber)
    }

    @Test
    fun doesNotOpenOverAWindowWithoutAStream() {
        finder.nextOrOpen("inventory")

        assertNull(finder.state.value)
    }

    @Test
    fun selectsTheNewestMatchFirstAndStepsUpTheBuffer() {
        lines("an orc", "nothing", "Orc and orc")
        finder.nextOrOpen(MAIN_WINDOW_NAME)
        finder.setQuery("orc")

        var state = finder.state.value!!
        assertEquals(3, state.totalMatches)
        assertEquals(1, state.currentNumber)
        assertEquals(serialOf(2), state.currentSerial)
        assertEquals(8 until 11, state.currentRange)

        finder.next()
        state = finder.state.value!!
        assertEquals(serialOf(2), state.currentSerial)
        assertEquals(0 until 3, state.currentRange)

        finder.next()
        assertEquals(serialOf(0), finder.state.value!!.currentSerial)

        // Wraps back round to the newest.
        finder.next()
        assertEquals(1, finder.state.value!!.currentNumber)

        finder.previous()
        assertEquals(3, finder.state.value!!.currentNumber)
    }

    @Test
    fun theFindMacroStepsOnceOpen() {
        lines("orc", "orc")
        finder.nextOrOpen(MAIN_WINDOW_NAME)
        finder.setQuery("orc")

        finder.nextOrOpen(MAIN_WINDOW_NAME)
        assertEquals(2, finder.state.value!!.currentNumber)

        finder.previousOrOpen(MAIN_WINDOW_NAME)
        assertEquals(1, finder.state.value!!.currentNumber)
    }

    @Test
    fun closingForgetsTheQuery() {
        lines("orc")
        finder.nextOrOpen(MAIN_WINDOW_NAME)
        finder.setQuery("orc")
        finder.setFocused(true)

        finder.close()

        assertNull(finder.state.value)
        assertEquals(false, finder.focused.value)
        finder.nextOrOpen(MAIN_WINDOW_NAME)
        assertEquals("", finder.state.value!!.query)
    }

    @Test
    fun aQueryWhileClosedIsIgnored() {
        lines("orc")
        finder.setQuery("orc")

        assertNull(finder.state.value)
    }
}
