import co.touchlab.kermit.Severity
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythProtocolHandler
import warlockfe.warlock3.wrayth.protocol.WraythUnhandledTagEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WraythUnknownTagTests {
    private fun unhandled(events: List<WraythEvent>) = events.filterIsInstance<WraythUnhandledTagEvent>()

    @Test
    fun anUnknownTagIsReportedAsAFirstSighting() {
        val events = WraythProtocolHandler().parseLine("<neverSeenBefore/>")

        assertEquals(listOf(WraythUnhandledTagEvent("neverSeenBefore", firstSighting = true)), unhandled(events))
    }

    @Test
    fun theSameTagIsOnlyAFirstSightingOnce() {
        // The handler still reports the tag every time, so nothing downstream has to guess whether a
        // tag it did not act on was seen; only the once carries the flag that surfaces it.
        val handler = WraythProtocolHandler()

        val first = unhandled(handler.parseLine("<neverSeenBefore/>")).single()
        val second = unhandled(handler.parseLine("<neverSeenBefore/>")).single()
        val third = unhandled(handler.parseLine("<neverSeenBefore/>")).single()

        assertTrue(first.firstSighting)
        assertEquals(listOf(false, false), listOf(second.firstSighting, third.firstSighting))
        assertEquals("neverSeenBefore", third.tag)
    }

    @Test
    fun eachDistinctTagGetsItsOwnFirstSighting() {
        val handler = WraythProtocolHandler()

        val a = unhandled(handler.parseLine("<alpha/>")).single()
        val b = unhandled(handler.parseLine("<beta/>")).single()
        val aAgain = unhandled(handler.parseLine("<alpha/>")).single()

        assertTrue(a.firstSighting)
        assertTrue(b.firstSighting)
        assertTrue(!aAgain.firstSighting)
    }

    @Test
    fun pulseIsIgnoredRatherThanUnhandled() {
        val events = WraythProtocolHandler().parseLine("<pulse/>")

        assertEquals(emptyList(), unhandled(events))
    }

    @Test
    fun aTagGeneratorCannotKeepAnnouncingItself() {
        // Past the reporting cap the parser goes quiet, so a server sending a fresh tag name every
        // line cannot fill the stream with them.
        val handler = WraythProtocolHandler()
        repeat(256) { handler.parseLine("<generated$it/>") }

        val beyondTheCap = unhandled(handler.parseLine("<generated999/>")).single()

        assertTrue(!beyondTheCap.firstSighting)
    }

    @Test
    fun debugIsMoreVerboseThanInfo() {
        // WraythClient shows unhandled tags when `minSeverity <= Severity.Debug`. That reads either
        // way round at a glance, and getting it backwards would show them only in the builds meant
        // not to, so pin which direction is which.
        assertTrue(Severity.Debug <= Severity.Debug)
        assertTrue(Severity.Verbose <= Severity.Debug)
        assertTrue(Severity.Info > Severity.Debug)
        assertTrue(Severity.Warn > Severity.Debug)
    }
}
