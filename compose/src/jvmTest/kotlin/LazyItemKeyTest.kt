import warlockfe.warlock3.compose.ui.window.StreamLine
import warlockfe.warlock3.compose.ui.window.StreamTextLine
import warlockfe.warlock3.compose.ui.window.lazyItemKeyModulus
import warlockfe.warlock3.compose.ui.window.serialSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lazy list folds line serial numbers into a bounded key space, because Compose caches one
 * entry per distinct item key forever. That is only safe while the fold keeps the keys of the lines
 * on screen at once distinct - a collision there is a duplicate key in a LazyColumn.
 */
class LazyItemKeyTest {
    private fun line(serialNumber: Long): StreamLine =
        StreamTextLine(
            text = null,
            entireLineStyle = null,
            serialNumber = serialNumber,
            showWhenClosed = null,
            isPrompt = false,
        )

    // The displayed lines occupy a contiguous serial range; a filter only removes lines from inside it.
    @Test
    fun serialSpanMeasuresTheRangeNotTheCount() {
        assertEquals(0L, emptyList<StreamLine>().serialSpan())
        assertEquals(1L, listOf(line(7)).serialSpan())
        assertEquals(5L, listOf(line(10), line(11), line(12), line(13), line(14)).serialSpan())
        // A name filter hiding the middle leaves the same range.
        assertEquals(5L, listOf(line(10), line(14)).serialSpan())
    }

    @Test
    fun modulusExceedsTheSpanItHasToSeparate() {
        for (span in listOf(0L, 1L, 100L, 2_000L, 4_095L, 4_096L, 10_000L, 250_000L)) {
            val modulus = lazyItemKeyModulus(span)
            assertTrue(modulus > span, "modulus $modulus must exceed span $span to keep keys distinct")
        }
    }

    // A span this wide needs more lines than fit in memory, so it is unreachable - but the whole
    // point of this function is distinct keys, so it must not have a size at which it stops
    // delivering them and returns a colliding modulus instead.
    @Test
    fun modulusHandlesSpansTooLargeToFold() {
        for (span in listOf(1L shl 40, (1L shl 40) + 1, 1L shl 41, Long.MAX_VALUE / 4)) {
            val modulus = lazyItemKeyModulus(span)
            assertTrue(modulus > span, "modulus $modulus must exceed span $span")
        }
        // Past the point where doubling would overflow, the fold degrades to the identity rather
        // than wrapping negative and collapsing back to the floor.
        for (span in listOf(Long.MAX_VALUE / 2, Long.MAX_VALUE - 1, Long.MAX_VALUE)) {
            assertEquals(Long.MAX_VALUE, lazyItemKeyModulus(span), "span $span should stop folding")
        }
    }

    // Serial numbers around the overflow boundary still have to key distinctly.
    @Test
    fun keysAreDistinctForAnEnormousSpan() {
        val span = Long.MAX_VALUE / 2
        val modulus = lazyItemKeyModulus(span)
        val serials = listOf(0L, 1L, span / 2, span - 1, span)
        assertEquals(serials.size, serials.mapTo(HashSet()) { it % modulus }.size)
    }

    @Test
    fun modulusStaysBoundedAndStableForTheDefaultScrollback() {
        // The default 2000-line buffer must land on one value and stay there, so rows are not
        // re-keyed (a full recomposition) as the buffer fills.
        val duringWarmup = (0L..2_000L step 97).map { lazyItemKeyModulus(it) }
        assertEquals(setOf(4_096L), duringWarmup.toSet())
    }

    // The property that matters: no two lines on screen at once may share a key.
    @Test
    fun keysAreDistinctAcrossAFullBuffer() {
        for (bufferSize in listOf(1, 2, 2_000, 5_000, 20_000)) {
            // Start well past zero: serial numbers keep counting up for the life of the connection,
            // so the fold has to hold at any offset, not just the first buffer.
            val firstSerial = 987_654_321L
            val lines = (0 until bufferSize).map { line(firstSerial + it) }
            val modulus = lazyItemKeyModulus(lines.serialSpan())
            val keys = lines.mapTo(HashSet()) { it.serialNumber % modulus }
            assertEquals(
                bufferSize,
                keys.size,
                "buffer of $bufferSize produced ${keys.size} distinct keys at modulus $modulus",
            )
        }
    }

    // Keys have to be reused once a line is gone - that reuse is what bounds the cache.
    @Test
    fun keysRecycleAsLinesScrollPast() {
        val bufferSize = 2_000
        val modulus = lazyItemKeyModulus(bufferSize.toLong())
        val seen = (0L until 100_000L).mapTo(HashSet()) { it % modulus }
        assertEquals(
            modulus.toInt(),
            seen.size,
            "100k lines should reuse the whole key space rather than growing it",
        )
    }
}
