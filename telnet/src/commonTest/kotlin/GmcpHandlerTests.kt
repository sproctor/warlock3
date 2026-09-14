import warlockfe.warlock3.core.client.DataDistance
import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.core.client.Percentage
import warlockfe.warlock3.core.compass.Direction
import warlockfe.warlock3.telnet.gmcp.GmcpHandler
import warlockfe.warlock3.telnet.gmcp.GmcpUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GmcpHandlerTests {
    private fun directions(vararg names: String) = names.map { Direction(it) }.toSet()

    private fun bars(update: GmcpUpdate?): List<PanelObject.ProgressBar> =
        (update as GmcpUpdate.Vitals).objects.filterIsInstance<PanelObject.ProgressBar>()

    @Test
    fun roomExitsFromAnObjectLightTheCompass() {
        val update = GmcpHandler().handle("Room.Info", """{"num":1,"name":"A field","exits":{"n":2,"se":3,"u":4,"out":5}}""")
        assertEquals(GmcpUpdate.Exits(directions("n", "se", "up", "out")), update)
    }

    @Test
    fun roomExitsFromAListAndLongNames() {
        val update = GmcpHandler().handle("room.info", """{"exits":["north","Down","west","portal"]}""")
        assertEquals(GmcpUpdate.Exits(directions("n", "down", "w")), update)
    }

    @Test
    fun vitalsWithMaximaBecomeBars() {
        val update =
            GmcpHandler().handle(
                "Char.Vitals",
                """{"hp":"1500","maxhp":"3000","mp":"400","maxmp":"400","ep":"2000","maxep":"2500","wp":"10","maxwp":"40","nl":"12"}""",
            )
        val bars = bars(update)
        assertEquals(listOf("health", "mana", "stamina", "spirit"), bars.map { it.id })
        assertEquals(listOf(50, 100, 80, 25), bars.map { it.value.value })
        assertEquals("health 1500/3000", bars[0].text)
        // Four bars share the width equally, left to right.
        assertEquals(listOf(0, 25, 50, 75), bars.map { (it.left as DataDistance.Percent).value.value })
        assertEquals(DataDistance.Percent(Percentage(25)), bars[0].width)
        // Each bar has a skin entry named as GS4 names them, so a GS4 skin colours it.
        val skins = (update as GmcpUpdate.Vitals).objects.filterIsInstance<PanelObject.Skin>()
        assertEquals(listOf("healthBar", "manaBar", "staminaBar", "spiritBar"), skins.map { it.name })
        assertEquals(listOf("health"), skins[0].controls)
    }

    @Test
    fun maximaSentSeparatelyAreRemembered() {
        val handler = GmcpHandler()
        // Aardwolf: the maxima come once, the vitals keep coming.
        assertNull(handler.handle("char.maxstats", """{"maxhp":200,"maxmana":100,"maxmoves":50}""")?.let { bars(it).ifEmpty { null } })
        val bars = bars(handler.handle("char.vitals", """{"hp":100,"mana":25,"moves":50}"""))
        assertEquals(listOf("health 100/200", "mana 25/100", "stamina 50/50"), bars.map { it.text })
        assertEquals(listOf(50, 25, 100), bars.map { it.value.value })
    }

    @Test
    fun vitalsWithoutMaximaShowTheNumber() {
        val bars = bars(GmcpHandler().handle("Char.Vitals", """{"hp":"73"}"""))
        assertEquals("health 73", bars.single().text)
        assertEquals(73, bars.single().value.value)
        val large = bars(GmcpHandler().handle("Char.Vitals", """{"hp":"7300"}"""))
        assertEquals(100, large.single().value.value)
    }

    @Test
    fun wieldedItemsFillTheHands() {
        val handler = GmcpHandler()
        val list =
            handler.handle(
                "Char.Items.List",
                """{"location":"inv","items":[
                    {"id":"1","name":"a leather backpack","attrib":"wc"},
                    {"id":"2","name":"a steel longsword","attrib":"L"},
                    {"id":"3","name":"an oak shield","attrib":"l"}
                ]}""",
            )
        assertEquals(GmcpUpdate.Hands(left = "an oak shield", right = "a steel longsword"), list)

        // Unwielding the shield and wielding a dagger in its place, as the server reports each.
        assertEquals(
            GmcpUpdate.Hands(left = null, right = "a steel longsword"),
            handler.handle("Char.Items.Update", """{"location":"inv","item":{"id":"3","name":"an oak shield","attrib":""}}"""),
        )
        assertEquals(
            GmcpUpdate.Hands(left = "a dagger", right = "a steel longsword"),
            handler.handle("Char.Items.Add", """{"location":"inv","item":{"id":"4","name":"a dagger","attrib":"l"}}"""),
        )
        assertEquals(
            GmcpUpdate.Hands(left = "a dagger", right = null),
            handler.handle("Char.Items.Remove", """{"location":"inv","item":{"id":"2","name":"a steel longsword","attrib":"L"}}"""),
        )
        // An older server removes by bare id.
        assertEquals(
            GmcpUpdate.Hands(left = null, right = null),
            handler.handle("Char.Items.Remove", """{"location":"inv","item":"4"}"""),
        )
    }

    @Test
    fun wearableAndWornItemsStayOutOfTheHands() {
        // W is wearable (and not worn), w is worn: neither is a hand.
        val hands =
            GmcpHandler().handle(
                "Char.Items.List",
                """{"location":"inv","items":[{"id":"1","name":"a cloak","attrib":"W"},{"id":"2","name":"a helm","attrib":"w"}]}""",
            )
        assertEquals(GmcpUpdate.Hands(left = null, right = null), hands)
    }

    @Test
    fun roomAndContainerListingsLeaveTheHandsAlone() {
        val handler = GmcpHandler()
        assertNull(handler.handle("Char.Items.List", """{"location":"room","items":[{"id":"9","name":"a rock","attrib":"L"}]}"""))
        assertNull(handler.handle("Char.Items.Add", """{"location":"rep12","item":{"id":"9","name":"a rock","attrib":"l"}}"""))
    }

    @Test
    fun unknownAndMalformedMessagesAreIgnored() {
        val handler = GmcpHandler()
        assertNull(handler.handle("Char.Name", """{"name":"Bob"}"""))
        assertNull(handler.handle("Char.Vitals", "not json"))
        assertNull(handler.handle("Char.Vitals", """{"string":"H:10/20"}"""))
        assertNull(handler.handle("Room.Info", ""))
    }
}
