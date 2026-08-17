import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.wrayth.network.LineBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LineBufferTests {
    private fun LineBuffer.append(text: String) = append(StyledString(text))

    @Test
    fun textAppendedWithNoComponentOpenBelongsToTheLine() {
        val buffer = LineBuffer()

        buffer.append("one ")
        buffer.append("two")

        assertEquals("one two", buffer.takeLine()?.toText())
    }

    @Test
    fun takingTheLineEmptiesIt() {
        val buffer = LineBuffer()
        buffer.append("first line")
        buffer.takeLine()

        assertEquals("", buffer.takeLine()?.toText())
    }

    @Test
    fun anEmptyLineIsStillALine() {
        // A blank line renders as one, so the caller needs an empty string here rather than null -
        // null is reserved for "a component is open, do not flush yet".
        assertEquals("", LineBuffer().takeLine()?.toText())
    }

    @Test
    fun textAppendedWhileAComponentIsOpenBelongsToTheComponent() {
        val buffer = LineBuffer()

        buffer.append("line ")
        buffer.openComponent("room objs")
        buffer.append("a dummy")

        assertEquals("room objs" to "a dummy", buffer.closeComponent()?.let { it.first to it.second.toText() })
        assertEquals("line ", buffer.takeLine()?.toText())
    }

    @Test
    fun theLineCannotBeTakenWhileAComponentIsOpen() {
        val buffer = LineBuffer()
        buffer.append("line ")
        buffer.openComponent("room objs")

        assertNull(buffer.takeLine())
    }

    @Test
    fun closingWithNothingOpenReportsNothing() {
        assertNull(LineBuffer().closeComponent())
    }

    @Test
    fun closingAComponentReturnsTheLineToGatheringItsOwnText() {
        val buffer = LineBuffer()
        buffer.append("before ")
        buffer.openComponent("room objs")
        buffer.append("a dummy")
        buffer.closeComponent()

        buffer.append("after")

        assertEquals("before after", buffer.takeLine()?.toText())
    }

    @Test
    fun discardingAComponentReturnsTheLineToGatheringItsOwnText() {
        // The case a prompt or a stream switch hits: a component is open and its content is
        // abandoned. Whatever comes next is the line's again, and the line has to stay flushable -
        // when these came apart, every later line went somewhere nothing flushed and the stream
        // fell permanently silent.
        val buffer = LineBuffer()
        buffer.openComponent("room objs")
        buffer.append("abandoned")

        buffer.discardComponent()
        buffer.append("after the prompt")

        assertEquals("after the prompt", buffer.takeLine()?.toText())
    }

    @Test
    fun aDiscardedComponentIsNotReportedByAClosingTagThatFollows() {
        val buffer = LineBuffer()
        buffer.openComponent("room objs")
        buffer.append("abandoned")
        buffer.discardComponent()

        assertNull(buffer.closeComponent())
    }

    @Test
    fun openingAComponentDoesNotCarryThePreviousOnesText() {
        val buffer = LineBuffer()
        buffer.openComponent("first")
        buffer.append("first content")
        buffer.closeComponent()

        buffer.openComponent("second")
        buffer.append("second content")

        assertEquals("second content", buffer.closeComponent()?.second?.toText())
    }
}
