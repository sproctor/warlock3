package warlockfe.warlock3.core.prefs.config

import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Saving a config rewrites the whole file, so it must carry the user's hand-written comments over.
 * Comments are matched to highlights/names by `id` so they follow an entry across a reorder.
 */
class CharacterConfigCommentTest {
    private val fs = SystemFileSystem
    private lateinit var dir: Path
    private lateinit var configDir: String

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("config-comment-test").toAbsolutePath().toString())
        configDir = dir.toString()
        fs.createDirectories(Path(configDir, "characters"))
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private fun globalFile() = Path(Path(configDir, "characters"), "$GLOBAL_CHARACTER_ID.toml")

    private fun write(
        path: Path,
        text: String,
    ) = fs.sink(path).buffered().use { it.writeString(text) }

    private fun read(path: Path): String = fs.source(path).buffered().use { it.readString() }

    @Test
    fun comments_survive_save_and_follow_their_entry_across_reorder() =
        runBlocking {
            write(
                globalFile(),
                """
                # warlock global highlights
                character = "global"

                [[highlights]]
                # APPLE: keep this one
                id = "11111111-1111-1111-1111-111111111111"
                pattern = "apple"
                ignoreCase = true # on purpose

                [[highlights]]
                # BANANA
                id = "22222222-2222-2222-2222-222222222222"
                pattern = "banana"
                """.trimIndent(),
            )

            val store = CharacterConfigStore(configDir, fs)
            store.load()

            // Reverse the highlight order, the same kind of edit the reorder UI makes.
            store.mutate(GLOBAL_CHARACTER_ID) { it.copy(highlights = it.highlights.reversed()) }

            val out = read(globalFile())

            // The section banner and both per-entry comments survived the rewrite.
            assertTrue("# warlock global highlights" in out, "banner comment lost:\n$out")
            assertTrue("# APPLE: keep this one" in out, "apple comment lost:\n$out")
            assertTrue("# BANANA" in out, "banana comment lost:\n$out")
            assertTrue("# on purpose" in out, "inline comment lost:\n$out")

            // The order changed: banana is now first.
            assertTrue(
                out.indexOf("pattern = \"banana\"") < out.indexOf("pattern = \"apple\""),
                "highlights not reordered:\n$out",
            )
            // Each comment followed its own entry: banana's comment is now above apple's, and each
            // sits immediately before its own pattern.
            assertTrue(out.indexOf("# BANANA") < out.indexOf("# APPLE: keep this one"), "comments did not move:\n$out")
            assertTrue(out.indexOf("# BANANA") < out.indexOf("pattern = \"banana\""), "banana comment detached:\n$out")
            assertTrue(out.indexOf("# APPLE: keep this one") < out.indexOf("pattern = \"apple\""), "apple comment detached:\n$out")
        }
}
