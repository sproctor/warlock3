import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.wrayth.protocol.WraythActionEvent
import warlockfe.warlock3.wrayth.protocol.WraythDialogObjectEvent
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

    private inline fun <reified T : DialogObject> parseDialogObject(line: String): T =
        WraythProtocolHandler()
            .parseLine(line)
            .filterIsInstance<WraythDialogObjectEvent>()
            .map { it.data }
            .filterIsInstance<T>()
            .single()

    @Test
    fun dropDownBoxParsesOptionsAndCommand() {
        val box =
            parseDialogObject<DialogObject.DropDownBox>(
                "<dropDownBox id='dDBAim' value='head' cmd='aim %dDBAim%' " +
                    "content_text='random,head,neck' content_value='rnd,hd,nk'/>",
            )

        assertEquals("dDBAim", box.id)
        assertEquals("head", box.value)
        assertEquals("aim %dDBAim%", box.cmd)
        assertEquals(
            listOf(
                DialogObject.DropDownBox.Option("random", "rnd"),
                DialogObject.DropDownBox.Option("head", "hd"),
                DialogObject.DropDownBox.Option("neck", "nk"),
            ),
            box.options,
        )
    }

    @Test
    fun radioParsesSelectionAndCommand() {
        val box =
            parseDialogObject<DialogObject.Radio>(
                "<radio id=\"bothRad\" value=\"1\" text=\"Both\" cmd=\"_injury 2\" group=\"injureMode\"/>",
            )

        assertEquals("bothRad", box.id)
        assertEquals(true, box.selected)
        assertEquals("Both", box.text)
        assertEquals("_injury 2", box.cmd)
        assertEquals("injureMode", box.group)
    }

    @Test
    fun unselectedRadioIsNotSelected() {
        assertEquals(false, parseDialogObject<DialogObject.Radio>("<radio id=\"r\" value=\"0\" text=\"x\"/>").selected)
    }

    @Test
    fun upDownEditBoxParsesBounds() {
        val box = parseDialogObject<DialogObject.UpDownEditBox>("<upDownEditBox id='uDEQuickstrike' min='-60' max='60' value='-1'/>")

        assertEquals("uDEQuickstrike", box.id)
        assertEquals(-1, box.value)
        assertEquals(-60, box.min)
        assertEquals(60, box.max)
    }
}
