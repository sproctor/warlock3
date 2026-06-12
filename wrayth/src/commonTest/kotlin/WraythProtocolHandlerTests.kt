import warlockfe.warlock3.wrayth.protocol.WraythActionEvent
import warlockfe.warlock3.wrayth.protocol.WraythProtocolHandler
import kotlin.test.Test
import kotlin.test.assertEquals

class WraythProtocolHandlerTests {
    @Test
    fun commandLinkProducesActionEvent() {
        val events = WraythProtocolHandler().parseLine("<d cmd=\"go north\">north</d>")

        assertEquals(
            WraythActionEvent(text = "north", command = "go north"),
            events.filterIsInstance<WraythActionEvent>().single(),
        )
    }

    @Test
    fun commandLinkWithoutCmdUsesTextAsCommand() {
        val events = WraythProtocolHandler().parseLine("<d>look</d>")

        assertEquals(
            WraythActionEvent(text = "look", command = "look"),
            events.filterIsInstance<WraythActionEvent>().single(),
        )
    }

    @Test
    fun twoCommandLinksInOneLineAreIndependent() {
        val events =
            WraythProtocolHandler()
                .parseLine("<d cmd=\"go north\">north</d> or <d cmd=\"go south\">south</d>")
                .filterIsInstance<WraythActionEvent>()

        assertEquals(
            listOf(
                WraythActionEvent("north", "go north"),
                WraythActionEvent("south", "go south"),
            ),
            events,
        )
    }

    @Test
    fun consecutiveCommandLinksDoNotLeakState() {
        // One handler parsing two command links in a row must not carry command/text between them -
        // the per-element state lives in the protocol handler's tag stack, not the shared DHandler.
        val handler = WraythProtocolHandler()
        val first = handler.parseLine("<d cmd=\"go north\">north</d>").filterIsInstance<WraythActionEvent>().single()
        val second = handler.parseLine("<d cmd=\"swim\">into the river</d>").filterIsInstance<WraythActionEvent>().single()

        assertEquals(WraythActionEvent("north", "go north"), first)
        assertEquals(WraythActionEvent("into the river", "swim"), second)
    }
}
