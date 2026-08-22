package warlockfe.warlock3.compose.ui.window

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the window's selection clear reads. The cases that matter are the ones where the serial
 * numbers alone cannot tell the collector that its rows are gone.
 */
class BufferChangesTest {
    private fun buffer(vararg serials: Long): List<StreamLine> = serials.map { StreamImageLine(url = "x", serialNumber = it) }

    @Test
    fun aTrimReportsTheNewOldestSerial() =
        runTest {
            val lines = MutableStateFlow(buffer(0, 1, 2))
            val generations = MutableStateFlow(0)
            val seen = mutableListOf<BufferChange>()
            val collector = launch { bufferChanges(lines, generations).collect { seen += it } }
            runCurrent()

            lines.value = buffer(1, 2)

            runCurrent()
            collector.cancel()
            assertEquals(listOf(BufferChange(oldestSerial = 1, restarted = false)), seen)
        }

    // A clear restarts the serial numbers, and the flow it publishes on conflates: the clear and
    // the line that follows it arrive as one value, so the buffer is never seen empty.
    @Test
    fun aClearFollowedByALineStillReportsARestart() =
        runTest {
            val lines = MutableStateFlow(buffer(500, 501))
            val generations = MutableStateFlow(0)
            val seen = mutableListOf<BufferChange>()
            val collector = launch { bufferChanges(lines, generations).collect { seen += it } }
            runCurrent()

            // Both halves of ComposeTextStream.clear(), then an append, before the collector runs.
            generations.value += 1
            lines.value = emptyList()
            lines.value = buffer(0)

            runCurrent()
            collector.cancel()
            assertEquals(listOf(BufferChange(oldestSerial = 0, restarted = true)), seen)
        }

    // The same on a window that never filled, where the oldest serial was 0 before the clear and is
    // 0 after it. Nothing about the serials changed, so only the generation says the rows went.
    @Test
    fun aClearOnANeverTrimmedBufferStillReportsARestart() =
        runTest {
            val lines = MutableStateFlow(buffer(0, 1, 2))
            val generations = MutableStateFlow(0)
            val seen = mutableListOf<BufferChange>()
            val collector = launch { bufferChanges(lines, generations).collect { seen += it } }
            runCurrent()

            generations.value += 1
            lines.value = buffer(0)

            runCurrent()
            collector.cancel()
            assertEquals(listOf(BufferChange(oldestSerial = 0, restarted = true)), seen)
        }

    @Test
    fun theStateACollectorArrivesToIsNotAChange() =
        runTest {
            val lines = MutableStateFlow(buffer(7, 8))
            val generations = MutableStateFlow(3)
            val seen = mutableListOf<BufferChange>()
            val collector = launch { bufferChanges(lines, generations).collect { seen += it } }
            runCurrent()

            runCurrent()
            collector.cancel()
            assertEquals(emptyList(), seen)
        }
}
