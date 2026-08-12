import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.wrayth.protocol.WraythActionEvent
import warlockfe.warlock3.wrayth.protocol.WraythCloseDialogEvent
import warlockfe.warlock3.wrayth.protocol.WraythDialogObjectEvent
import warlockfe.warlock3.wrayth.protocol.WraythDialogWindowEvent
import warlockfe.warlock3.wrayth.protocol.WraythProtocolHandler
import warlockfe.warlock3.wrayth.protocol.WraythUnhandledTagEvent
import warlockfe.warlock3.wrayth.util.WraythDialogWindow
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

    private inline fun <reified T : PanelObject> parsePanelObject(line: String): T =
        WraythProtocolHandler()
            .parseLine(line)
            .filterIsInstance<WraythDialogObjectEvent>()
            .map { it.data }
            .filterIsInstance<T>()
            .single()

    @Test
    fun dropDownBoxParsesOptionsAndCommand() {
        val box =
            parsePanelObject<PanelObject.DropDownBox>(
                "<dropDownBox id='dDBAim' value='head' cmd='aim %dDBAim%' " +
                    "content_text='random,head,neck' content_value='rnd,hd,nk'/>",
            )

        assertEquals("dDBAim", box.id)
        assertEquals("head", box.value)
        assertEquals("aim %dDBAim%", box.cmd)
        assertEquals(
            listOf(
                PanelObject.DropDownBox.Option("random", "rnd"),
                PanelObject.DropDownBox.Option("head", "hd"),
                PanelObject.DropDownBox.Option("neck", "nk"),
            ),
            box.options,
        )
    }

    @Test
    fun radioParsesSelectionAndCommand() {
        val box =
            parsePanelObject<PanelObject.Radio>(
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
        assertEquals(false, parsePanelObject<PanelObject.Radio>("<radio id=\"r\" value=\"0\" text=\"x\"/>").selected)
    }

    @Test
    fun menuLinkParsesExistAndNoun() {
        // A row from the GS4 befriend (Friends & Enemies) panel.
        val link =
            parsePanelObject<PanelObject.MenuLink>(
                "<menuLink id=\"friend1\" value=\"Medikel\" exist=\"-10246518\" noun=\"Medikel\" " +
                    "align=\"nw\" top=\"0\" left=\"0\" width=\"100\"/>",
            )

        assertEquals("friend1", link.id)
        assertEquals("Medikel", link.value)
        assertEquals("-10246518", link.exist)
        assertEquals("Medikel", link.noun)
        assertEquals("nw", link.align)
    }

    @Test
    fun menuImageParsesFaceAndTooltip() {
        val image =
            parsePanelObject<PanelObject.MenuImage>(
                "<menuImage id=\"demeanor1\" name=\"friendlyFace\" tooltip=\"Friendly\" exist=\"-10246518\" " +
                    "noun=\"Medikel\" align=\"nw\" top=\"0\" left=\"100\" height=\"25\" width=\"25\"/>",
            )

        assertEquals("demeanor1", image.id)
        assertEquals("friendlyFace", image.name)
        assertEquals("Friendly", image.tooltip)
        assertEquals("-10246518", image.exist)
        assertEquals("Medikel", image.noun)
    }

    @Test
    fun befriendRowParsesAllThreeWidgets() {
        // A full befriend dialogData line as the game sends it: name link, demeanor face, remove
        // button. The remove image keeps its cmd/echo pair.
        val objects =
            WraythProtocolHandler()
                .parseLine(
                    "<dialogData id=\"befriend\"><menuLink id=\"friend1\" value=\"Medikel\" exist=\"-10246518\" " +
                        "noun=\"Medikel\" align=\"nw\" top=\"0\" left=\"0\" width=\"100\"/>" +
                        "<menuImage id=\"demeanor1\" name=\"friendlyFace\" tooltip=\"Friendly\" exist=\"-10246518\" " +
                        "noun=\"Medikel\" align=\"nw\" top=\"0\" left=\"100\" height=\"25\" width=\"25\"/>" +
                        "<image id='remove1' name='crossFace' cmd='befriend clear 1' align='nw' top=\"0\" " +
                        "left=\"130\" height=\"25\" width=\"25\" tooltip=\"Remove\" echo=\"befriend clear 1\"/>" +
                        "</dialogData>",
                ).filterIsInstance<WraythDialogObjectEvent>()
                .map { it.data }

        assertEquals(3, objects.size)
        val remove = objects.filterIsInstance<PanelObject.Image>().single()
        assertEquals("crossFace", remove.name)
        assertEquals("befriend clear 1", remove.cmd)
        assertEquals("befriend clear 1", remove.echo)
        assertEquals("Remove", remove.tooltip)
    }

    @Test
    fun upDownEditBoxParsesBounds() {
        val box = parsePanelObject<PanelObject.UpDownEditBox>("<upDownEditBox id='uDEQuickstrike' min='-60' max='60' value='-1'/>")

        assertEquals("uDEQuickstrike", box.id)
        assertEquals(-1, box.value)
        assertEquals(-60, box.min)
        assertEquals(60, box.max)
    }

    private fun parseDialogWindow(line: String): WraythDialogWindow =
        WraythProtocolHandler()
            .parseLine(line)
            .filterIsInstance<WraythDialogWindowEvent>()
            .single()
            .window

    @Test
    fun openDialogKeepsLocationAndResident() {
        // The shape the game sends for a permanent panel: content nested in the tag, on one line.
        val window =
            parseDialogWindow(
                "<openDialog type='dynamic' id='combat' title='Combat' location='right' target='combat' " +
                    "height='219' resident='true'><dialogData id='combat' clear='t'></dialogData></openDialog>",
            )

        assertEquals("combat", window.id)
        assertEquals("Combat", window.title)
        assertEquals("dynamic", window.type)
        assertEquals("right", window.location)
        assertEquals(true, window.resident)
    }

    @Test
    fun openDialogCollapsesMnemonicAmpersandsInTitle() {
        // The befriend panel's title uses Windows menu-mnemonic escaping on the wire: Wrayth
        // displays "Friends &amp;&amp; Enemies" as "Friends & Enemies".
        val window =
            parseDialogWindow(
                "<openDialog type=\"dynamic\" id=\"befriend\" title=\"Friends &amp;&amp; Enemies\" " +
                    "location=\"right\" target=\"befriend\" height=\"165\" resident=\"true\">" +
                    "<dialogData id=\"befriend\"></dialogData></openDialog>",
            )

        assertEquals("befriend", window.id)
        assertEquals("Friends & Enemies", window.title)
        assertEquals(true, window.resident)
    }

    @Test
    fun openDialogWithoutResidentIsTransient() {
        val window =
            parseDialogWindow(
                "<openDialog type='dynamic' id='bank' title='Bank' location='right'>" +
                    "<dialogData id='bank'></dialogData></openDialog>",
            )

        assertEquals(false, window.resident)
        assertEquals("right", window.location)
    }

    @Test
    fun closeDialogProducesCloseEvent() {
        val events = WraythProtocolHandler().parseLine("<closeDialog id=\"bank\"/>")

        assertEquals(
            WraythCloseDialogEvent(id = "bank"),
            events.filterIsInstance<WraythCloseDialogEvent>().single(),
        )
    }

    @Test
    fun closeDialogWithoutIdIsIgnored() {
        val events = WraythProtocolHandler().parseLine("<closeDialog/>")

        assertEquals(emptyList(), events.filterIsInstance<WraythCloseDialogEvent>())
    }

    @Test
    fun tagNamesAreMatchedCaseSensitively() {
        // The real client reads <opendialog> as a tag it has never heard of and drops it on the
        // floor, no error, no panel. Ours used to lowercase every name and so accepted spellings
        // the game never sends - which hid a miscased tag in our own lab bootstrap for months.
        val handled = WraythProtocolHandler().parseLine("<openDialog id=\"bank\" title=\"Bank\"/>")
        assertEquals(1, handled.filterIsInstance<WraythDialogWindowEvent>().size)

        val miscased = WraythProtocolHandler().parseLine("<opendialog id=\"bank\" title=\"Bank\"/>")
        assertEquals(emptyList(), miscased.filterIsInstance<WraythDialogWindowEvent>())
        assertEquals(
            listOf("opendialog"),
            miscased.filterIsInstance<WraythUnhandledTagEvent>().map { it.tag },
        )
    }

    @Test
    fun tagsWeKnowinglyIgnoreAreNotReportedAsUnhandled() {
        // A tag carrying nothing we use is registered all the same, so the unhandled channel keeps
        // meaning "we have never seen this" rather than "this is one of the forty we ignore".
        val events = WraythProtocolHandler().parseLine("<timestamp time=\"1700000000\"/>")

        assertEquals(emptyList(), events.filterIsInstance<WraythUnhandledTagEvent>())
    }
}
