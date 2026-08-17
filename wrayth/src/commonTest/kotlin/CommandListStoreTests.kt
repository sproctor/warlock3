import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import warlockfe.warlock3.wrayth.util.CmdDefinition
import warlockfe.warlock3.wrayth.util.CommandListStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandListStoreTests {
    private var counter = 0

    private fun store(): CommandListStore {
        // A directory of its own per store, so one test cannot read another's file.
        val dir = Path(SystemTemporaryDirectory, "cmdlist-test-${counter++}-${this.hashCode()}")
        SystemFileSystem.createDirectories(dir)
        return CommandListStore(dir.toString())
    }

    private fun cmd(
        coord: String,
        command: String = "look at #",
        menu: String = "look at @",
        category: String = "1",
    ) = CmdDefinition(coord = coord, command = command, menu = menu, category = category)

    @Test
    fun nothingCachedYet() {
        assertNull(store().load("GS4"))
    }

    @Test
    fun aSavedListComesBackWithItsSerial() {
        val store = store()
        val commands = listOf(cmd("2524,1587"), cmd("2524,2462", command = "stab # left leg"))

        store.save("GS4", "1776042608.1.1.1", commands)
        val loaded = store.load("GS4")

        assertEquals("1776042608.1.1.1", loaded?.serial)
        assertEquals(commands, loaded?.commands)
    }

    @Test
    fun everyFieldSurvivesTheRoundTrip() {
        val store = store()
        val command = cmd(coord = "2524,12632", command = "cman dislodge #", menu = "dislodge @", category = "6_combat maneuvers")

        store.save("GS4", "1.1.1.1", listOf(command))

        assertEquals(command, store.load("GS4")?.commands?.single())
    }

    @Test
    fun markupInACommandSurvivesTheRoundTrip() {
        // Menu text is server-supplied and does reach for punctuation - "swear at @ thornily" is real.
        // Anything that would end an attribute early has to come back as it went in.
        val store = store()
        val command =
            cmd(
                coord = "2524,1692",
                command = """say "hi" & <wave>""",
                menu = """say "hi" & <wave> @""",
                category = "5_roleplay & such",
            )

        store.save("GS4", "1.1.1.1", listOf(command))

        assertEquals(command, store.load("GS4")?.commands?.single())
    }

    @Test
    fun savingAgainReplacesWhatWasThere() {
        val store = store()
        store.save("GS4", "1.1.1.1", listOf(cmd("2524,1"), cmd("2524,2")))

        store.save("GS4", "2.1.1.1", listOf(cmd("2524,3")))

        val loaded = store.load("GS4")
        assertEquals("2.1.1.1", loaded?.serial)
        assertEquals(listOf(cmd("2524,3")), loaded?.commands)
    }

    @Test
    fun listsAreKeptApartByName() {
        val store = store()
        store.save("GS4", "1.1.1.1", listOf(cmd("2524,1")))
        store.save("DR", "2.1.1.1", listOf(cmd("9999,1")))

        assertEquals("1.1.1.1", store.load("GS4")?.serial)
        assertEquals(listOf(cmd("9999,1")), store.load("DR")?.commands)
    }

    @Test
    fun aListNameCannotEscapeTheCacheDirectory() {
        val store = store()

        store.save("../../escaped", "1.1.1.1", listOf(cmd("2524,1")))

        // Whatever it wrote, it read back under the same name and nowhere above the cache directory.
        assertEquals(listOf(cmd("2524,1")), store.load("../../escaped")?.commands)
        assertTrue(SystemFileSystem.exists(Path(SystemTemporaryDirectory, "cmdlists")).not())
    }

    @Test
    fun anEmptyListStillRecordsItsSerial() {
        // "You are up to date" is worth remembering; otherwise the next login asks from scratch.
        val store = store()

        store.save("GS4", "5.1.1.1", emptyList())

        assertEquals("5.1.1.1", store.load("GS4")?.serial)
        assertEquals(emptyList(), store.load("GS4")?.commands)
    }
}
