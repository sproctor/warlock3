import warlockfe.warlock3.core.client.Effect
import warlockfe.warlock3.core.client.EffectPanel
import warlockfe.warlock3.core.client.GameObject
import warlockfe.warlock3.core.client.GameState
import warlockfe.warlock3.core.client.Injury
import warlockfe.warlock3.core.client.Vital
import warlockfe.warlock3.wrayth.network.GameStateTracker
import warlockfe.warlock3.wrayth.network.parseRoomTitle
import warlockfe.warlock3.wrayth.protocol.WraythProtocolHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// Fixtures are lines as GemStone IV and DragonRealms send them, trimmed: each is well formed on its
// own, since the protocol never carries a tag across lines.
class GameStateTrackerTests {
    private val now = Instant.fromEpochSeconds(1_700_000_000)

    private fun feed(vararg lines: String): GameState {
        val handler = WraythProtocolHandler()
        val tracker = GameStateTracker(now = { now })
        lines.forEach { line -> handler.parseLine(line).forEach { tracker.handle(it) } }
        return tracker.state.value
    }

    private val roomLines =
        arrayOf(
            """<nav rm='4043031'/><streamWindow id='main' title='Story' subtitle=" - Clovertooth Hall, Main Lobby - 4043031" location='center' target='drop'/><streamWindow id='room' title='Room' subtitle=" - Clovertooth Hall, Main Lobby - 4043031" location='center' target='drop' ifClosed='' resident='true'/><clearStream id='room'/><pushStream id='room'/><compDef id='room desc'>A <a exist="-53404" noun="statue">dark riverstone statue</a> occupies the center of the lobby, under a <a exist="-53402" noun="dome">painted dome</a>.</compDef><compDef id='room objs'>  You also see the <a exist="252651311" noun="disk">Gnimbus disk</a>,<b> <pushBold/>a <a exist="252640454" noun="rowl">black-billed jet black rowl</a><popBold/></b> that is flying around and a <a exist="-53440" noun="staircase">carved white stone staircase</a> climbing to the second floor.</compDef>""",
            """<compDef id='room players'>Also here: <a exist="-11208595" noun="Demid">Demid</a> who is sitting, <a exist="-11230853" noun="Gnimbus">Gnimbus</a></compDef>""",
            """<compDef id='room exits'>Obvious exits: <d>north</d>, <d>east</d>, <d>out</d></compDef>""",
            """<compDef id='sprite'></compDef><popStream id='room'/>""",
            """<compass><dir value="n"/><dir value="e"/><dir value="out"/></compass><prompt time="1767457947">&gt;</prompt>""",
        )

    @Test
    fun roomFromNavAndComponents() {
        val room = feed(*roomLines).room

        assertEquals("4043031", room.id)
        assertEquals("Clovertooth Hall, Main Lobby", room.title)
        assertEquals("4043031", room.uid)
        assertEquals("A dark riverstone statue occupies the center of the lobby, under a painted dome.", room.description)
        assertEquals(listOf("-53404", "-53402"), room.descriptionObjects.map { it.id })
        assertEquals(listOf("north", "east", "out"), room.exits)
        assertEquals(setOf("n", "e", "out"), room.compass)
    }

    @Test
    fun boldObjectsAreCreaturesAndTheRestLoot() {
        val room = feed(*roomLines).room

        assertEquals(
            listOf(GameObject(id = "252640454", noun = "rowl", name = "black-billed jet black rowl", status = "flying around")),
            room.npcs,
        )
        assertEquals(listOf("252651311", "-53440"), room.loot.map { it.id })
        assertEquals(listOf(null, null), room.loot.map { it.status })
    }

    @Test
    fun playersWithTheirStatus() {
        val pcs = feed(*roomLines).room.pcs

        assertEquals(
            listOf(
                GameObject(id = "-11208595", noun = "Demid", name = "Demid", status = "sitting"),
                GameObject(id = "-11230853", noun = "Gnimbus", name = "Gnimbus", status = null),
            ),
            pcs,
        )
    }

    @Test
    fun creatureStatusesFromTheRoomText() {
        val npcs =
            feed(
                """<component id='room objs'>You also see <pushBold/>a <a exist="1" noun="kobold">kobold</a><popBold/> that appears stunned, <pushBold/>a <a exist="2" noun="rat">giant rat</a><popBold/> that appears dead and <pushBold/>a <a exist="3" noun="orc">orc</a><popBold/> that appears to be frozen in place.</component>""",
            ).room.npcs

        assertEquals(listOf("stunned", "dead", "frozen in place"), npcs.map { it.status })
    }

    @Test
    fun aRefreshedComponentReplacesTheOldContents() {
        val state =
            feed(
                *roomLines,
                """<component id='room objs'>You also see <pushBold/>a <a exist="7" noun="kobold">kobold</a><popBold/>.</component>""",
                """<component id='room players'></component>""",
            )

        assertEquals(listOf("7"), state.room.npcs.map { it.id })
        assertEquals(emptyList(), state.room.loot)
        assertEquals(emptyList(), state.room.pcs)
        // Untouched by the refresh.
        assertEquals("4043031", state.room.id)
        assertEquals(listOf("north", "east", "out"), state.room.exits)
    }

    @Test
    fun navClearsTheRoom() {
        val room =
            feed(
                *roomLines,
                """<nav rm='4043032'/><streamWindow id='main' title='Story' subtitle=" - Clovertooth Hall, Stairs" location='center' target='drop'/>""",
            ).room

        assertEquals("4043032", room.id)
        assertEquals("Clovertooth Hall, Stairs", room.title)
        assertEquals(emptyList(), room.npcs)
        assertEquals(emptyList(), room.loot)
        assertEquals(emptyList(), room.pcs)
        assertNull(room.description)
    }

    @Test
    fun handsWithTheirExistIds() {
        val state =
            feed(
                """<spell>None</spell><left exist="1924528" noun="shield">reinforced shield</left><right>Empty</right>""",
            )

        assertEquals(GameObject(id = "1924528", noun = "shield", name = "reinforced shield"), state.leftHand)
        assertNull(state.rightHand)
        assertNull(state.preparedSpell)

        val casting = feed("""<spell exist='spell'>Minor Shock</spell>Your hands glow with power.""")
        assertEquals("Minor Shock", casting.preparedSpell)
    }

    @Test
    fun vitals() {
        val vitals =
            feed(
                """<dialogData id='minivitals'><skin id='manaSkin' name='manaBar' controls='mana' left='25%' top='0%' width='25%' height='100%'/><progressBar id='mana' value='90' text='mana 381/423' left='25%' customText='t' top='0%' width='25%' height='100%'/></dialogData>""",
                """<dialogData id='injuries'><progressBar id='health2' value='100' text='health 167/167' customText='t' align='n' top='160' width='140' left='0' height='15'/></dialogData>""",
                """<dialogData id='combat'><progressBar id='pbarStance' value='100' text='defensive (100%)' top='51' width='130' height='16' left='0' align='n'/></dialogData>""",
                """<dialogData id='encum'><progressBar id='encumlevel' value='20' text='Somewhat' top='5' left='-5' align='n' width='160' height='15'/></dialogData>""",
                """<dialogData id='expr'><progressBar id='mindState' value='0' text='clear as a bell' top='45' left='3' align='n' width='160' height='15'/></dialogData>""",
            ).vitals

        assertEquals(
            mapOf(
                "mana" to Vital(percent = 90, text = "mana 381/423", current = 381, max = 423),
                "stance" to Vital(percent = 100, text = "defensive (100%)"),
                "encumbrance" to Vital(percent = 20, text = "Somewhat"),
                "mind" to Vital(percent = 0, text = "clear as a bell"),
            ),
            vitals,
        )
    }

    @Test
    fun effectsAreReplacedWhenThePanelIsCleared() {
        val buffs =
            """<dialogData id='Buffs' clear='t'></dialogData><dialogData id='Buffs'><progressBar id='115' value='23' text="115" left='22%' top='0' width='76%' height='15' time='00:58:25'/><label id='l115' value='0:58 ' top='0' left='0' justify='2' anchor_right=''/><progressBar id='5006' value='47' text="Sign of Warding" left='22%' top='16' width='76%' height='15' time='00:08:21'/></dialogData>"""
        val effects =
            feed(
                buffs,
                """<dialogData id='Debuffs' clear='t'></dialogData><dialogData id='Debuffs'><progressBar id='188130976' value='0' text="Creeping Dread (1)" left='22%' top='0' width='76%' height='15' time='00:01:00'/></dialogData>""",
            ).effects

        assertEquals(
            listOf(
                Effect(id = "115", name = "115", percent = 23, expiresAt = now + 58.minutes + 25.seconds),
                Effect(id = "5006", name = "Sign of Warding", percent = 47, expiresAt = now + 8.minutes + 21.seconds),
            ),
            effects[EffectPanel.BUFFS],
        )
        assertEquals(listOf("Creeping Dread (1)"), effects[EffectPanel.DEBUFFS]?.map { it.name })

        val cleared =
            feed(
                buffs,
                """<dialogData id='Buffs' clear='t'></dialogData><dialogData id='Buffs'><progressBar id='5006' value='45' text="Sign of Warding" left='22%' top='0' width='76%' height='15' time='03:11:38'/></dialogData>""",
            ).effects[EffectPanel.BUFFS]
        assertEquals(listOf(Effect("5006", "Sign of Warding", 45, now + 3.hours + 11.minutes + 38.seconds)), cleared)
    }

    @Test
    fun targets() {
        val state =
            feed(
                """<dialogData id='combat'><dropDownBox id='dDBTarget' value="skeletal dreadsteed" cmd="target %dDBTarget%" tooltip='Select Target' content_text="skeletal dreadsteed,mist-wreathed banshee" content_value="#254427867,#254427750" height='20' width='80' top='93' left='0' align='n'/></dialogData>""",
            )
        assertEquals(listOf("254427867", "254427750"), state.targetIds)
        assertEquals("skeletal dreadsteed", state.currentTarget)

        val none =
            feed(
                """<dialogData id='combat'><dropDownBox id='dDBTarget' value="none" cmd="target %dDBTarget%" tooltip='Select Target' content_text="none" content_value="target help" height='20' width='80' top='93' left='0' align='n'/></dialogData>""",
            )
        assertEquals(emptyList(), none.targetIds)
        assertNull(none.currentTarget)
    }

    @Test
    fun injuries() {
        val injuries =
            feed(
                """<dialogData id='injuries'><image id='back' name='Injury2' height='0' width='0'/><image id='rightEye' name='Scar1' height='0' width='0'/><image id='neck' name='neck' height='0' width='0'/></dialogData>""",
                """<dialogData id='injuries'><image id='rightEye' name='rightEye' height='0' width='0'/></dialogData>""",
            ).injuries

        assertEquals(mapOf("back" to Injury(Injury.Kind.INJURY, 2)), injuries)
    }

    @Test
    fun creatureStatusTagsAreMergedByExistAndClearedOnNav() {
        val tagged =
            feed(
                """<crtrStatus exist="65662200" hostile="1"/><prompt time="1787170202">&gt;</prompt>""",
                """<crtrStatus exist="65662200" disengaged="1"/><prompt time="1787170203">&gt;</prompt>""",
            )
        assertEquals(mapOf("65662200" to mapOf("hostile" to "1", "disengaged" to "1")), tagged.creatureStatus)

        val moved = feed("""<crtrStatus exist="65662200" hostile="1"/>""", """<nav rm='1'/>""")
        assertEquals(emptyMap(), moved.creatureStatus)
    }

    @Test
    fun roomTitles() {
        assertEquals("Silverwood Manor, Courtyard" to null, parseRoomTitle(" - Silverwood Manor, Courtyard"))
        assertEquals("Clovertooth Hall, Main Lobby" to "4043031", parseRoomTitle(" - Clovertooth Hall, Main Lobby - 4043031"))
        assertEquals("The Crossing, Betany Street" to "10007", parseRoomTitle(" - [The Crossing, Betany Street] (10007)"))
        assertEquals("Town Green" to null, parseRoomTitle(" - [Town Green]"))
        assertEquals(null to null, parseRoomTitle(""))
    }
}
