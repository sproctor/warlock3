import warlockfe.warlock3.wrayth.protocol.WraythCliEvent
import warlockfe.warlock3.wrayth.protocol.WraythCmdTimestampEvent
import warlockfe.warlock3.wrayth.protocol.WraythProtocolHandler
import warlockfe.warlock3.wrayth.protocol.WraythUpdateVerbsEvent
import warlockfe.warlock3.wrayth.util.CmdDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class WraythCommandListTests {
    @Test
    fun updateVerbsCarriesTheListName() {
        val events = WraythProtocolHandler().parseLine("""<updateverbs default="GS4"/>""")

        assertEquals(WraythUpdateVerbsEvent("GS4"), events.first())
    }

    @Test
    fun updateVerbsWithoutADefaultNamesNoList() {
        val events = WraythProtocolHandler().parseLine("<updateverbs/>")

        assertEquals(WraythUpdateVerbsEvent(null), events.first())
    }

    @Test
    fun cmdTimestampCarriesTheSerial() {
        val events = WraythProtocolHandler().parseLine("""<cmdtimestamp data='1776042608.1.1.1'/>""")

        assertEquals(WraythCmdTimestampEvent("1776042608.1.1.1"), events.first())
    }

    @Test
    fun aListAndItsSerialArriveOnOneLine() {
        // Exactly as a real GemStone connection sends it: the serial follows </cmdlist> on the same
        // line, so the entries it names are already in hand by the time it is read.
        val events =
            WraythProtocolHandler().parseLine(
                "<cmdlist>" +
                    """<cli coord="2524,2295" menu="garrote stop" command="garrote stop" menu_cat="6"/>""" +
                    """<cli coord="2524,12632" menu="dislodge @" command="cman dislodge #" menu_cat="6_combat maneuvers"/>""" +
                    "</cmdlist><cmdtimestamp data='1776042608.1.1.1'/>",
            )

        assertEquals(
            listOf(
                CmdDefinition(coord = "2524,2295", menu = "garrote stop", command = "garrote stop", category = "6"),
                CmdDefinition(
                    coord = "2524,12632",
                    menu = "dislodge @",
                    command = "cman dislodge #",
                    category = "6_combat maneuvers",
                ),
            ),
            events.filterIsInstance<WraythCliEvent>().map { it.cmd },
        )
        assertEquals(WraythCmdTimestampEvent("1776042608.1.1.1"), events.filterIsInstance<WraythCmdTimestampEvent>().single())
    }
}
