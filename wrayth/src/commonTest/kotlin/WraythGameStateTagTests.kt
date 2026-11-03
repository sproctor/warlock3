import warlockfe.warlock3.core.client.CreatureStatus
import warlockfe.warlock3.core.client.ObjectiveAction
import warlockfe.warlock3.core.client.ObjectiveReward
import warlockfe.warlock3.core.client.ObjectiveTask
import warlockfe.warlock3.core.client.RoomMeta
import warlockfe.warlock3.core.client.RoomWeather
import warlockfe.warlock3.wrayth.protocol.WraythCreatureStatusEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythNavEvent
import warlockfe.warlock3.wrayth.protocol.WraythObjectiveChildEvent
import warlockfe.warlock3.wrayth.protocol.WraythObjectiveEvent
import warlockfe.warlock3.wrayth.protocol.WraythObjectivesEndEvent
import warlockfe.warlock3.wrayth.protocol.WraythObjectivesStartEvent
import warlockfe.warlock3.wrayth.protocol.WraythProtocolHandler
import warlockfe.warlock3.wrayth.protocol.WraythPulseEvent
import warlockfe.warlock3.wrayth.protocol.WraythRoomMetaEvent
import warlockfe.warlock3.wrayth.protocol.WraythUnhandledTagEvent
import warlockfe.warlock3.wrayth.util.ObjectiveBuilder
import warlockfe.warlock3.wrayth.util.applyObjectives
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

// Tags carrying game state that the Wrayth client draws nothing for, read the way Saga (0.10.7)
// reads them. The quoted lines are as GemStone and DragonRealms sent them.
class WraythGameStateTagTests {
    private fun parse(line: String): List<WraythEvent> =
        WraythProtocolHandler().parseLine(line).also { events ->
            assertTrue(events.none { it is WraythUnhandledTagEvent }, "unhandled tag in $events")
        }

    @Test
    fun navCarriesTheRoomId() {
        assertEquals(WraythNavEvent("4043031"), parse("<nav rm='4043031'/>").filterIsInstance<WraythNavEvent>().single())
    }

    @Test
    fun aBareNavIsARoomWithoutAnId() {
        assertEquals(WraythNavEvent(null), parse("<nav/>").filterIsInstance<WraythNavEvent>().single())
    }

    @Test
    fun pulseSaysWhichPoolIsNextAndDefaultsTheWindow() {
        assertEquals(
            WraythPulseEvent(manaNext = true, minSeconds = 46, maxSeconds = 75),
            parse("<pulse mana=\"1\"/>").filterIsInstance<WraythPulseEvent>().single(),
        )
        assertEquals(
            WraythPulseEvent(manaNext = false, minSeconds = 30, maxSeconds = 60),
            parse("<pulse mana=\"0\" min=\"30\" max=\"60\"/>").filterIsInstance<WraythPulseEvent>().single(),
        )
    }

    @Test
    fun crtrStatusFlagsAndHealth() {
        assertEquals(
            CreatureStatus("64968701", hostile = false, disengaged = true, effects = emptyList(), health = null, maxHealth = null),
            parse("<crtrStatus exist=\"64968701\" hostile=\"0\" disengaged=\"1\"/><prompt time=\"1787164544\">&gt;</prompt>")
                .filterIsInstance<WraythCreatureStatusEvent>()
                .single()
                .status,
        )
        assertEquals(
            CreatureStatus(
                "123",
                hostile = true,
                disengaged = false,
                effects = listOf("stunned", "bleeding"),
                health = 40,
                maxHealth = 120,
            ),
            parse(
                "<crtrStatus exist=\"123\" hostile=\"1\" stunned=\"1\" prone=\"0\" condition=\"bleeding\" health=\"40\" maxhealth=\"120\"/>",
            ).filterIsInstance<WraythCreatureStatusEvent>()
                .single()
                .status,
        )
    }

    @Test
    fun roomMeta() {
        assertEquals(
            RoomMeta(
                climate = 6,
                terrain = 1,
                realm = 7,
                inside = false,
                sanctuary = false,
                bonfire = false,
                locker = false,
                waterLevel = 0,
                weather = null,
            ),
            parse(
                "<roommeta weather=\"0\" bonfire=\"0\" inside=\"0\" water=\"0\" sanctuary=\"0\" realm=\"7\" climate=\"6\" terrain=\"1\"/>",
            ).filterIsInstance<WraythRoomMetaEvent>()
                .single()
                .roomMeta,
        )
        val stormy =
            parse("<roommeta weather=\"1\" inside=\"y\" water2=\"1\" locker=\"true\" climate=\"2\" terrain=\"3\"/>")
                .filterIsInstance<WraythRoomMetaEvent>()
                .single()
                .roomMeta
        assertEquals(RoomWeather.STORM, stormy.weather)
        assertEquals(true, stormy.inside)
        assertEquals(2, stormy.waterLevel)
        assertTrue(stormy.locker)
    }

    @Test
    fun roomMetaWithoutClimateAndTerrainSaysNothing() {
        assertTrue(parse("<roommeta weather=\"0\" realm=\"7\"/>").none { it is WraythRoomMetaEvent })
    }

    @Test
    fun objectivesBatchArrivesInOrder() {
        val events =
            parse(
                "<objectives action='patch-objective'><objective id='24542' type='quest' state='active' name='Artificer Research'>" +
                    "<task text='Unbind a Gemstone' count='1' max='1' done='t'/><reward type='fame' amount='500'/>" +
                    "<action type='abandon' cmd='QUEST ABANDON 24542'/></objective></objectives>",
            )
        assertEquals(
            listOf(
                WraythObjectivesStartEvent::class,
                WraythObjectiveEvent::class,
                WraythObjectiveChildEvent::class,
                WraythObjectiveChildEvent::class,
                WraythObjectiveChildEvent::class,
                WraythObjectivesEndEvent::class,
            ),
            events
                .filter {
                    it is WraythObjectivesStartEvent || it is WraythObjectiveEvent || it is WraythObjectiveChildEvent ||
                        it == WraythObjectivesEndEvent
                }.map { it::class },
        )
        assertEquals("patch-objective", events.filterIsInstance<WraythObjectivesStartEvent>().single().action)
    }

    @Test
    fun objectiveBuilderReadsSagasFields() {
        val builder =
            ObjectiveBuilder(
                mapOf(
                    "id" to "24542",
                    "type" to "quest",
                    "state" to "active",
                    "name" to "Artificer Research",
                    "location" to "variable",
                    "cadence" to "weekly",
                    "expires" to "1790000000",
                ),
            )
        builder.addChild("task", mapOf("text" to "Unbind a Gemstone", "count" to "1", "max" to "1", "done" to "t"))
        builder.addChild("task", mapOf("text" to "Amplify a Gemstone", "count" to "0", "max" to "1"))
        builder.addChild("reward", mapOf("type" to "experience", "amount" to "2000"))
        builder.addChild("reward", mapOf("type" to "custom", "label" to "a gem pouch, 3 silvers"))
        builder.addChild("reward", mapOf("type" to "fame"))
        builder.addChild("action", mapOf("type" to "abandon", "cmd" to "QUEST ABANDON 24542", "tooltip" to "Give up"))
        val objective = builder.build()

        assertEquals("QUEST-24542", objective.key)
        assertEquals("", objective.location)
        assertEquals("weekly", objective.cadence)
        assertEquals(Instant.fromEpochSeconds(1790000000), objective.expires)
        assertEquals(
            listOf(
                ObjectiveTask("Unbind a Gemstone", 1, 1, null, done = true),
                ObjectiveTask("Amplify a Gemstone", 0, 1, null, done = false),
            ),
            objective.tasks,
        )
        assertEquals(
            listOf(
                ObjectiveReward("experience", 2000, null),
                ObjectiveReward("custom", null, "a gem pouch"),
                ObjectiveReward("custom", null, "3 silvers"),
            ),
            objective.rewards,
        )
        assertEquals(listOf(ObjectiveAction("abandon", "QUEST ABANDON 24542", "Give up")), objective.actions)
    }

    @Test
    fun objectiveActions() {
        fun objective(
            id: String,
            type: String?,
            name: String = "",
        ) = ObjectiveBuilder(listOfNotNull("id" to id, type?.let { "type" to it }, "name" to name).toMap()).build()

        val bounty = objective("17755", "BOUNTY", "Slay")
        val quest = objective("24542", "QUEST", "Research")
        val all = applyObjectives(emptyList(), "full-refresh", listOf(bounty, quest))
        assertEquals(listOf(bounty, quest), all)

        val renamed = objective("24542", "QUEST", "Research, part 2")
        assertEquals(listOf(bounty, renamed), applyObjectives(all, "patch-objective", listOf(renamed)))

        // As GemStone sends it: <objective id='17755' type='BOUNTY'/>
        assertEquals(listOf(quest), applyObjectives(all, "delete-objective", listOf(objective("17755", "BOUNTY"))))
        // Same id, other type: not this one
        assertEquals(all, applyObjectives(all, "delete-objective", listOf(objective("17755", "QUEST"))))
        // No type: any objective with that id
        assertEquals(listOf(bounty), applyObjectives(all, "delete-objective", listOf(objective("24542", null))))

        assertEquals(emptyList(), applyObjectives(all, null, emptyList()))
    }
}
