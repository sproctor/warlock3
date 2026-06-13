package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandHistoryRepositoryTest {
    private val fs = SystemFileSystem
    private lateinit var dir: Path
    private lateinit var configDir: String

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("history-test").toAbsolutePath().toString())
        configDir = dir.toString()
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private fun newRepo() = CommandHistoryRepository(CharacterConfigStore(configDir, fs), fs, Dispatchers.Unconfined)

    private fun historyFile(characterId: String): Path {
        val (gameCode, name) = characterId.split(":")
        return Path(Path(Path(Path(configDir), "characters"), gameCode), name).let { Path(it, "history.txt") }
    }

    @Test
    fun load_missingFile_returnsEmpty() =
        runBlocking {
            assertEquals(emptyList(), newRepo().load("gs4:tholan"))
        }

    @Test
    fun save_thenLoad_roundTripsInOrder() =
        runBlocking {
            val repo = newRepo()
            val commands = listOf("look", "go north", "attack troll")
            repo.save("gs4:tholan", commands)

            assertEquals(commands, repo.load("gs4:tholan"))
        }

    @Test
    fun save_writesOneCommandPerLineOldestFirst() =
        runBlocking {
            val repo = newRepo()
            repo.save("gs4:tholan", listOf("look", "go north", "attack troll"))

            val text = fs.source(historyFile("gs4:tholan")).buffered().use { it.readString() }
            assertEquals("look\ngo north\nattack troll\n", text)
        }

    @Test
    fun load_ignoresBlankLines() =
        runBlocking {
            val repo = newRepo()
            val file = historyFile("gs4:tholan")
            file.parent?.let { fs.createDirectories(it) }
            fs.sink(file).buffered().use { it.writeString("look\n\ngo north\n") }

            assertEquals(listOf("look", "go north"), repo.load("gs4:tholan"))
        }

    @Test
    fun save_emptyList_deletesFile() =
        runBlocking {
            val repo = newRepo()
            repo.save("gs4:tholan", listOf("look"))
            assertTrue(fs.metadataOrNull(historyFile("gs4:tholan")) != null)

            repo.save("gs4:tholan", emptyList())
            assertFalse(fs.metadataOrNull(historyFile("gs4:tholan")) != null)
        }
}
