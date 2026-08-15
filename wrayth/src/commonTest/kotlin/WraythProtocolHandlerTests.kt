import warlockfe.warlock3.core.client.PanelJustify
import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.wrayth.protocol.WraythActionEvent
import warlockfe.warlock3.wrayth.protocol.WraythCloseDialogEvent
import warlockfe.warlock3.wrayth.protocol.WraythDialogObjectEvent
import warlockfe.warlock3.wrayth.protocol.WraythDialogWindowEvent
import warlockfe.warlock3.wrayth.protocol.WraythOpenUrlEvent
import warlockfe.warlock3.wrayth.protocol.WraythProtocolHandler
import warlockfe.warlock3.wrayth.protocol.WraythUnhandledTagEvent
import warlockfe.warlock3.wrayth.util.WraythDialogWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private fun parseLabelJustify(justify: String): PanelJustify =
        parsePanelObject<PanelObject.Label>("<label id='l' value='x' width='100%' justify='$justify'/>").justify

    @Test
    fun labelJustifyIsADrawTextMask() {
        // `justify` is a Win32 DrawText format mask, not an enum: DT_CENTER (1) beats DT_RIGHT (2),
        // and every other bit leaves the text on the left. Each of these was rendered by the real
        // client and read off the screen.
        assertEquals(listOf(0, 4, 8).map { PanelJustify.Left }, listOf(0, 4, 8).map { parseLabelJustify("$it") })
        assertEquals(
            listOf(1, 3, 5, 7, 9).map { PanelJustify.Center },
            listOf(1, 3, 5, 7, 9).map { parseLabelJustify("$it") },
        )
        assertEquals(listOf(2, 6, 10).map { PanelJustify.Right }, listOf(2, 6, 10).map { parseLabelJustify("$it") })
    }

    @Test
    fun missingOrEmptyJustifyCenters() {
        // Wrayth draws a label with no usable `justify` with a hardcoded 5 (DT_VCENTER or DT_CENTER).
        // An empty attribute takes that same path, unlike a non-empty one that merely is not a number.
        assertEquals(
            PanelJustify.Center,
            parsePanelObject<PanelObject.Label>("<label id='l' value='x' width='100%'/>").justify,
        )
        assertEquals(PanelJustify.Center, parseLabelJustify(""))
    }

    @Test
    fun labelJustifyThatIsNotANumberGoesLeft() {
        // Wrayth's string-to-number conversion yields no horizontal bits here, so it draws left.
        // Real game data only ever sends numbers; this pins the edge to what the client does.
        assertEquals(PanelJustify.Left, parseLabelJustify("right"))
    }

    private fun panelObjects(line: String): List<PanelObject> =
        WraythProtocolHandler()
            .parseLine(line)
            .filterIsInstance<WraythDialogObjectEvent>()
            .mapNotNull { it.data }

    @Test
    fun checkBoxCarriesTheValueAnotherWidgetSubstitutes() {
        // Straight off the wire, from a real combat panel.
        val box =
            parsePanelObject<PanelObject.CheckBox>(
                "<checkBox id='chkBoxCmbtpnl12' checked_value=\"on\" unchecked_value=\"off\" checked=\"\" " +
                    "text=\"quickstrike control group\" top='314' left='10' width='180' height='20' " +
                    "skin='check' align='nw'/>",
            )
        assertEquals("chkBoxCmbtpnl12", box.id)
        assertEquals("quickstrike control group", box.text)
        assertEquals("on", box.checkedValue)
        assertEquals("off", box.uncheckedValue)
    }

    @Test
    fun checkBoxIsCheckedByThePresenceOfTheAttribute() {
        // `checked` is presence-based, not a boolean: the real client ticks the box for every one of
        // these and leaves it clear only when the attribute is absent. Read off the screen for each.
        listOf("checked=''", "checked='off'", "checked='false'", "checked='0'", "checked='true'")
            .forEach { attr ->
                val box =
                    parsePanelObject<PanelObject.CheckBox>(
                        "<checkBox id='c' checked_value='on' unchecked_value='off' text='t' $attr/>",
                    )
                assertTrue(box.checked, "$attr should read as checked")
            }
        val absent =
            parsePanelObject<PanelObject.CheckBox>(
                "<checkBox id='c' checked_value='on' unchecked_value='off' text='t'/>",
            )
        assertFalse(absent.checked, "no attribute at all is the only unchecked state")
    }

    @Test
    fun checkBoxWithoutBothValuesIsDropped() {
        // The real client draws nothing for these - not an empty box, and no width either - because
        // a checkbox with no values has nothing to hand the command that reads it.
        listOf(
            "<checkBox id='c' text='t' checked=''/>",
            "<checkBox id='c' checked_value='on' text='t' checked=''/>",
            "<checkBox id='c' unchecked_value='off' text='t' checked=''/>",
        ).forEach { line ->
            assertEquals(emptyList(), panelObjects(line), "should be dropped: $line")
        }
    }

    @Test
    fun closeButtonIsACommandButtonThatAlsoCloses() {
        val button =
            parsePanelObject<PanelObject.Button>("<closeButton id='b' value='Done' cmd='saybye'/>")
        assertEquals("Done", button.value)
        assertEquals("saybye", button.cmd)
        assertTrue(button.closesPanel)
        // A cmdButton is the same widget without the closing half.
        val cmd = parsePanelObject<PanelObject.Button>("<cmdButton id='b' value='Go' cmd='go'/>")
        assertFalse(cmd.closesPanel)
    }

    @Test
    fun closeButtonWithoutACaptionIsDropped() {
        // There is no "Close" fallback: the real client draws nothing at all for a close button with
        // no value, even one given an explicit width and height.
        assertEquals(
            emptyList(),
            panelObjects("<closeButton id='b' cmd='saybye' width='80' height='26'/>"),
        )
    }

    @Test
    fun launchUrlIsRecognisedInTheCapitalisedFormGs4Sends() {
        // Straight off a GS4 session: the EXP window's Goals link, and the GOALS command, are both
        // answered with this. The tag is capitalised, which neither of the spellings we first
        // guessed matched once names started being compared exactly - so Goals silently launched
        // nothing. The src is a bare path; resolving it against the session's base URI happens in
        // the client, not here.
        val events =
            WraythProtocolHandler()
                .parseLine("<LaunchURL src=\"/gs4/play/cm/loader.asp?uname=X&gcode=GS4\"/>")
        assertEquals(
            listOf("/gs4/play/cm/loader.asp?uname=X&gcode=GS4"),
            events.filterIsInstance<WraythOpenUrlEvent>().map { it.url },
        )
        assertEquals(emptyList(), events.filterIsInstance<WraythUnhandledTagEvent>())
    }

    @Test
    fun dropDownBoxValueNamesTheLabelNotTheValue() {
        val box =
            parsePanelObject<PanelObject.DropDownBox>(
                "<dropDownBox id='dDBAim' value='neck' cmd='aim %dDBAim%' " +
                    "content_text='random,head,neck' content_value='rnd,hd,nk'/>",
            )
        // The real client shows "neck" for this and sends "aim nk" when a button reads it. Matching
        // `value` against the content_value column instead finds nothing, which is what left the
        // combat panel showing its first option while the game held another.
        assertEquals(PanelObject.DropDownBox.Option("neck", "nk"), box.serverOption)
        assertEquals(PanelObject.DropDownBox.Option("neck", "nk"), box.optionFor(null))
    }

    @Test
    fun dropDownBoxNamingNoLabelSelectsNothing() {
        // 'hd' is a content_value, not a label. The real client renders a literal "(ERROR)" here
        // rather than quietly selecting something.
        val box =
            parsePanelObject<PanelObject.DropDownBox>(
                "<dropDownBox id='dDBAim' value='hd' content_text='random,head,neck' " +
                    "content_value='rnd,hd,nk'/>",
            )
        assertNull(box.serverOption)
        assertNull(box.optionFor(null))
    }

    @Test
    fun dropDownBoxPrefersTheLocalSelectionOverTheServerLabel() {
        val box =
            parsePanelObject<PanelObject.DropDownBox>(
                "<dropDownBox id='dDBAim' value='neck' content_text='random,head,neck' " +
                    "content_value='rnd,hd,nk'/>",
            )
        // The panel's value map holds option values, since that is what `%<id>%` expands to.
        assertEquals(PanelObject.DropDownBox.Option("head", "hd"), box.optionFor("hd"))
    }

    @Test
    fun dropDownBoxUpdateStoresTheOptionValue() {
        val box =
            parsePanelObject<PanelObject.DropDownBox>(
                "<dropDownBox id='dDBAim' value='neck' content_text='random,head,neck' " +
                    "content_value='rnd,hd,nk'/>",
            )
        val values = mutableMapOf("dDBAim" to "hd")
        box.applyServerSelection(values)

        assertEquals(mapOf("dDBAim" to "nk"), values)
    }

    @Test
    fun dropDownBoxUpdateNamingNoLabelDropsTheStoredValue() {
        // The server named nothing this box has, so it has no selection - not the stale one, and not
        // the raw 'hd', which is a content_value rather than a label.
        val box =
            parsePanelObject<PanelObject.DropDownBox>(
                "<dropDownBox id='dDBAim' value='hd' content_text='random,head,neck' " +
                    "content_value='rnd,hd,nk'/>",
            )
        val values = mutableMapOf("dDBAim" to "nk")
        box.applyServerSelection(values)

        assertEquals(emptyMap(), values)
        assertNull(box.optionFor(values["dDBAim"]))
    }

    @Test
    fun dropDownBoxUpdateWithoutAValueKeepsTheLocalSelection() {
        // No `value` at all says nothing about the selection, so a local pick survives the update.
        val box =
            parsePanelObject<PanelObject.DropDownBox>(
                "<dropDownBox id='dDBAim' content_text='random,head,neck' content_value='rnd,hd,nk'/>",
            )
        val values = mutableMapOf("dDBAim" to "hd")
        box.applyServerSelection(values)

        assertEquals(mapOf("dDBAim" to "hd"), values)
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
    fun anEndlessStreamOfNewTagNamesDoesNotAccumulate() {
        // Nothing on the wire promises a finite set of tag names - the connection carries whatever
        // a Lich script emits - and the de-duplication set lives as long as the connection. Feed it
        // far more distinct names than the protocol has and it must still be reporting them as
        // unhandled without having kept them all.
        val handler = WraythProtocolHandler()
        repeat(2000) { i ->
            val events = handler.parseLine("<generated$i/>")
            assertEquals(listOf("generated$i"), events.filterIsInstance<WraythUnhandledTagEvent>().map { it.tag })
        }
    }

    @Test
    fun tagsWeKnowinglyIgnoreAreNotReportedAsUnhandled() {
        // A tag carrying nothing we use is registered all the same, so the unhandled channel keeps
        // meaning "we have never seen this" rather than "this is one of the forty we ignore".
        val events = WraythProtocolHandler().parseLine("<timestamp time=\"1700000000\"/>")

        assertEquals(emptyList(), events.filterIsInstance<WraythUnhandledTagEvent>())
    }
}
