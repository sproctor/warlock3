import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.prefs.SettingsProblems
import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.LoggingRepository
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.wrayth.network.WraythClient
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// The game state a WraythClient keeps from tags the Wrayth client draws nothing for.
class WraythGameStateTests {
    @Test
    fun roomMetaSentAheadOfNavIsKept() {
        // As GemStone logs in: the room's <roommeta> comes a few lines before its <nav>
        val client =
            run(
                "<mode id=\"GAME\"/>",
                "<roommeta weather=\"0\" bonfire=\"0\" inside=\"0\" water=\"0\" sanctuary=\"0\" realm=\"7\" climate=\"6\" terrain=\"1\"/>",
                "<nav rm='4130023'/>",
            )
        assertEquals(6, client.roomMeta.value?.climate)
    }

    @Test
    fun movingSetsTheRoomAndForgetsTheLastRoomsCreatures() {
        val client =
            run(
                "<mode id=\"GAME\"/>",
                "<nav rm='4043031'/>",
                "<roommeta weather=\"0\" bonfire=\"0\" inside=\"0\" water=\"0\" sanctuary=\"0\" realm=\"7\" climate=\"6\" terrain=\"1\"/>",
                "<crtrStatus exist=\"64968701\" hostile=\"0\" disengaged=\"1\"/><prompt time=\"1787164544\">&gt;</prompt>",
            )
        assertEquals("4043031", client.roomId.value)
        assertEquals(6, client.roomMeta.value?.climate)
        assertEquals(setOf("64968701"), client.creatures.value.keys)

        val moved =
            run(
                "<mode id=\"GAME\"/>",
                "<nav rm='4043031'/>",
                "<roommeta realm=\"7\" climate=\"6\" terrain=\"1\"/>",
                "<crtrStatus exist=\"64968701\" hostile=\"0\" disengaged=\"1\"/>",
                "<nav/>",
            )
        assertNull(moved.roomId.value)
        assertTrue(moved.creatures.value.isEmpty())
    }

    @Test
    fun aLaterCrtrStatusReplacesTheCreaturesEntry() {
        val client =
            run(
                "<mode id=\"GAME\"/>",
                "<crtrStatus exist=\"1\" hostile=\"1\"/>",
                "<crtrStatus exist=\"2\" hostile=\"1\"/>",
                "<crtrStatus exist=\"1\" hostile=\"1\" stunned=\"1\"/>",
            )
        assertEquals(listOf("stunned"), client.creatures.value["1"]?.effects)
        assertEquals(setOf("1", "2"), client.creatures.value.keys)
    }

    @Test
    fun roomMetaInAnotherStreamIsNotThisRoom() {
        val client =
            run(
                "<mode id=\"GAME\"/>",
                "<streamWindow id='thoughts' title='Thoughts'/>",
                "<pushStream id='thoughts'/><roommeta climate=\"6\" terrain=\"1\"/>far away<popStream/>",
            )
        assertNull(client.roomMeta.value)
    }

    @Test
    fun pulse() {
        val client = run("<mode id=\"GAME\"/>", "<pulse mana=\"1\"/>")
        val pulse = assertNotNull(client.lastPulse.value)
        assertTrue(pulse.manaNext)
    }

    @Test
    fun objectivesAreGatheredBatchByBatch() {
        val client =
            run(
                "<mode id=\"GAME\"/>",
                "<objectives action='full-refresh'><objective id='17755' type='BOUNTY' state='active' name='Slay Grizzled Creature'>" +
                    "<task text='Slay creatures in the Rift'/></objective>" +
                    "<objective id='24542' type='QUEST' state='active' name='Artificer Research'>" +
                    "<task text='Unbind a Gemstone' count='1' max='1' done='t'/></objective></objectives>",
                "<objectives action='delete-objective'><objective id='17755' type='BOUNTY'/></objectives>",
                // Outside <objectives>, these are nothing to do with them
                "<objective id='1' type='QUEST'/><task text='stray'/>",
            )
        val objectives = client.objectives.value
        assertEquals(listOf("QUEST-24542"), objectives.map { it.key })
        assertEquals(listOf("Unbind a Gemstone"), objectives.single().tasks.map { it.text })
    }

    private fun run(vararg lines: String): WraythClient =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val tempDir = Files.createTempDirectory("wrayth-state").toString()
            val configStore = ClientConfigStore(tempDir, SystemFileSystem)
            configStore.mutateClient { it.copy(logType = "NONE") }
            val dirs = WarlockDirs(homeDir = tempDir, dataDir = tempDir, configDir = tempDir, logDir = tempDir)
            val settings = ClientSettingRepository(InMemoryClientSettingDao(), configStore, dirs, SettingsProblems(tempDir))
            val client =
                WraythClient(
                    characterRepository = CharacterRepository(configStore),
                    windowRegistry = RecordingWindowRegistry(),
                    fileLogging = LoggingRepository(settings, scope),
                    ioDispatcher = Dispatchers.IO,
                    socket = ScriptedSocket(lines.toList()),
                    commandListStore = null,
                )
            client.connect("key")
            withTimeout(10.seconds) { client.disconnected.first { it } }
            scope.cancel()
            client
        }
}
