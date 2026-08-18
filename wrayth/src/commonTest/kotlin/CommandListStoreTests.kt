import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readString
import kotlinx.io.writeString
import warlockfe.warlock3.wrayth.util.CmdDefinition
import warlockfe.warlock3.wrayth.util.CommandListStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class CommandListStoreTests {
    // A directory of its own per store, so no test reads another's file - and none reads one left
    // behind by an earlier run, which a per-instance counter does not give you, since the test
    // framework builds a fresh instance per test and every one of them would start again at zero.
    private fun tempDir(): Path {
        val dir = Path(SystemTemporaryDirectory, "cmdlist-test-$RUN_ID-${counter++}")
        SystemFileSystem.createDirectories(dir)
        return dir
    }

    companion object {
        private val RUN_ID = Clock.System.now().toEpochMilliseconds()
        private var counter = 0
    }

    private fun store(): CommandListStore = CommandListStore(tempDir().toString())

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

    @Test
    fun aTruncatedCacheIsRefusedRatherThanTrusted() {
        // The nastiest shape this can take: a header carrying the serial, and only some of the
        // commands it promises. Trusting it would tell the server we are up to date while the
        // commands missing from it stayed missing, for as long as the serial did not change.
        val dir = tempDir()
        val store = CommandListStore(dir.toString())
        store.save("GS4", "1.1.1.1", listOf(cmd("2524,1"), cmd("2524,2"), cmd("2524,3")))
        val path = Path(dir, "cmdlists", "GS4.xml")
        val whole = SystemFileSystem.source(path).buffered().use { it.readString() }
        val truncated = whole.lineSequence().take(2).joinToString("\n") + "\n"
        SystemFileSystem.sink(path).buffered().use { it.writeString(truncated) }

        assertNull(store.load("GS4"))
    }

    @Test
    fun aCacheWithNoCountIsRefused() {
        val dir = tempDir()
        val store = CommandListStore(dir.toString())
        SystemFileSystem.createDirectories(Path(dir, "cmdlists"))
        SystemFileSystem.sink(Path(dir, "cmdlists", "GS4.xml")).buffered().use {
            it.writeString("<cmdlist timestamp=\"1.1.1.1\">\n</cmdlist>\n")
        }

        assertNull(store.load("GS4"))
    }

    @Test
    fun anInterruptedWriteLeavesThePreviousListInPlace() {
        // Tears the write for real: save() iterates the collection it is given, and this one stops
        // partway. Writing straight to the cache file would leave the header and two entries of a
        // list claiming four - which is exactly the shape load() must never be handed.
        val dir = tempDir()
        val store = CommandListStore(dir.toString())
        store.save("GS4", "1.1.1.1", listOf(cmd("2524,1")))

        store.save("GS4", "9.9.9.9", failingCollection(size = 4, failAfter = 2))

        val loaded = store.load("GS4")
        assertEquals("1.1.1.1", loaded?.serial)
        assertEquals(listOf(cmd("2524,1")), loaded?.commands)
    }

    /** A collection that yields [failAfter] entries and then gives up, reporting [size] all along. */
    private fun failingCollection(
        size: Int,
        failAfter: Int,
    ) = object : AbstractCollection<CmdDefinition>() {
        override val size = size

        override fun iterator() =
            object : Iterator<CmdDefinition> {
                private var yielded = 0

                override fun hasNext() = yielded < size

                override fun next(): CmdDefinition {
                    if (yielded >= failAfter) throw IllegalStateException("write interrupted")
                    return cmd("2524,${yielded++}")
                }
            }
    }

    @Test
    fun aSaveOverAnExistingListReplacesItWholesale() {
        val dir = tempDir()
        val store = CommandListStore(dir.toString())
        store.save("GS4", "1.1.1.1", List(5) { cmd("2524,$it") })

        store.save("GS4", "2.1.1.1", listOf(cmd("2524,99")))

        val loaded = store.load("GS4")
        assertEquals("2.1.1.1", loaded?.serial)
        assertEquals(listOf(cmd("2524,99")), loaded?.commands)
    }
}
