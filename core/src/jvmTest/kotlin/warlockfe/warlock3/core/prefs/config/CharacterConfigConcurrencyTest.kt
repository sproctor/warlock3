package warlockfe.warlock3.core.prefs.config

import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two app instances sharing a config dir (e.g. multi-boxing, which shares global.toml) must not
 * clobber each other's saves. Each [CharacterConfigStore.mutate] re-reads the file under a
 * cross-process lock and applies its transform to the current on-disk state.
 */
class CharacterConfigConcurrencyTest {
    private val fs = SystemFileSystem
    private lateinit var dir: Path
    private lateinit var configDir: String

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("config-concurrency-test").toAbsolutePath().toString())
        configDir = dir.toString()
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private fun addHighlight(
        id: String,
        pattern: String,
    ): (CharacterConfig) -> CharacterConfig = { it.copy(highlights = it.highlights + HighlightConfig(id = id, pattern = pattern)) }

    @Test
    fun second_instance_does_not_clobber_the_first() =
        runBlocking {
            // Both instances launch and load the same (empty) starting state.
            val instanceA = CharacterConfigStore(configDir, fs)
            val instanceB = CharacterConfigStore(configDir, fs)
            instanceA.load()
            instanceB.load()

            // A writes. B's in-memory snapshot is now stale (it never saw A's write).
            instanceA.mutate(GLOBAL_CHARACTER_ID, addHighlight("a", "apple"))
            instanceB.mutate(GLOBAL_CHARACTER_ID, addHighlight("b", "banana"))

            // A fresh load sees BOTH highlights: B re-read A's write before applying its own.
            val reloaded = CharacterConfigStore(configDir, fs)
            reloaded.load()
            val patterns = reloaded.current(GLOBAL_CHARACTER_ID).highlights.map { it.pattern }
            assertEquals(setOf("apple", "banana"), patterns.toSet(), "a save was lost: $patterns")
            assertTrue(patterns.size == 2, "expected exactly two highlights, got $patterns")
        }
}
