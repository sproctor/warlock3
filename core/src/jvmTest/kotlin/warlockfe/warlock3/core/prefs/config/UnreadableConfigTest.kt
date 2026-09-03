package warlockfe.warlock3.core.prefs.config

import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import org.junit.Assume.assumeTrue
import warlockfe.warlock3.core.prefs.SettingsUnreadableException
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A config file that is on disk but cannot be read must stop the launch, not be mistaken for one
 * that isn't there.
 *
 * These stores read a missing file as "nothing saved yet", which is right for a fresh install and
 * catastrophic for a damaged one: the character comes up with no highlights, and the next save
 * writes that emptiness over highlights that were never gone. Failing the load is what turns an
 * unreadable file into something the user can fix.
 */
class UnreadableConfigTest {
    private val fs = SystemFileSystem
    private lateinit var dir: Path

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("unreadable-config-test").toAbsolutePath().toString())
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .toFile()
            .walkBottomUp()
            .forEach {
                it.setReadable(true)
                if (it.isDirectory) it.setExecutable(true)
            }
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private fun write(
        path: Path,
        text: String,
    ) {
        fs.createDirectories(path.parent!!)
        fs.sink(path).buffered().use { it.writeString(text) }
    }

    /**
     * Deny reads on [path] and confirm it took. `setReadable(false)` is a request, not a guarantee:
     * root reads whatever it likes, and a filesystem that does not enforce POSIX modes ignores it
     * outright. Either way the store would load fine and the assertion below would fail for a
     * reason that has nothing to do with the code under test, so the test is skipped instead.
     */
    private fun denyReads(path: Path) {
        java.io.File(path.toString()).setReadable(false)
        val stillReadable = runCatching { fs.source(path).buffered().use { it.readByteArray() } }.isSuccess
        assumeTrue("filesystem does not enforce the unreadable mode (running as root?)", !stillReadable)
    }

    private fun highlightsFor(character: String) = Path(Path(Path(dir, "characters"), character), "highlights.toml")

    @Test
    fun aCharacterFileThePlatformWillNotLetUsReadStopsTheLoad() =
        runBlocking {
            val path = highlightsFor("global")
            write(path, "[[highlight]]\npattern = \"kobold\"\n")
            denyReads(path)

            val thrown = assertFailsWith<SettingsUnreadableException> { CharacterConfigStore(dir.toString(), fs).load() }

            assertContains(thrown.problem.headline, "highlights.toml")
            assertContains(thrown.problem.message, "will close")

            // Untouched: the settings are still there for a launch that can read them.
            java.io.File(path.toString()).setReadable(true)
            assertContains(fs.source(path).buffered().use { it.readByteArray().decodeToString() }, "kobold")
        }

    @Test
    fun aCharacterFileWeCannotParseStopsTheLoad() =
        runBlocking {
            // The config files are documented as hand-editable, so a typo in one is a thing that
            // happens -- and silently discarding the file would cost the user everything in it.
            write(highlightsFor("global"), "[[highlight]\npattern = \"kobold\"\n")

            val thrown = assertFailsWith<SettingsUnreadableException> { CharacterConfigStore(dir.toString(), fs).load() }

            assertContains(thrown.problem.headline, "highlights.toml")
        }

    @Test
    fun anUnreadableClientFileStopsTheLoad() =
        runBlocking {
            val path = Path(dir, "client.toml")
            write(path, "theme = \"DARK\"\n")
            denyReads(path)

            val thrown = assertFailsWith<SettingsUnreadableException> { ClientConfigStore(dir.toString(), fs).load() }

            assertContains(thrown.problem.headline, "client.toml")
        }

    /**
     * The case a stat check cannot see. A directory that lists but cannot be searched reports every
     * child as missing, so `metadataOrNull` on the file answers "not there" and the character's
     * settings would be silently replaced by defaults.
     */
    @Test
    fun aCharacterDirectoryWeCannotSearchStopsTheLoad() =
        runBlocking {
            val path = highlightsFor("global")
            write(path, "[[highlight]]\npattern = \"kobold\"\n")
            java.io.File(path.parent.toString()).setExecutable(false)
            val stillReadable = runCatching { fs.source(path).buffered().use { it.readByteArray() } }.isSuccess
            assumeTrue("filesystem does not enforce directory search permission", !stillReadable)

            val thrown = assertFailsWith<SettingsUnreadableException> { CharacterConfigStore(dir.toString(), fs).load() }

            assertContains(thrown.problem.headline, "highlights.toml")
        }

    @Test
    fun aMissingFileIsStillJustAFreshInstall() =
        runBlocking {
            // The whole point of the distinction: absent means "nothing saved yet" and must stay
            // that way, or every first launch would refuse to start.
            CharacterConfigStore(dir.toString(), fs).load()
            ClientConfigStore(dir.toString(), fs).load()

            assertEquals(emptyList(), CharacterConfigStore(dir.toString(), fs).current("global").highlights)
        }
}
