package warlockfe.warlock3.core.prefs.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The store reloads files changed out of band (hand edits, or writes from another app instance) so
 * observers see the latest content without a restart. Relies on the platform WatchService, so it
 * polls with a generous timeout rather than asserting an exact latency.
 */
class CharacterConfigWatchTest {
    private val fs = SystemFileSystem
    private lateinit var dir: Path
    private lateinit var configDir: String

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("config-watch-test").toAbsolutePath().toString())
        configDir = dir.toString()
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private fun globalFile() = Path(configDir, "$GLOBAL_CHARACTER_ID.toml")

    private fun globalToml(vararg patterns: String): String =
        buildString {
            appendLine("character = \"global\"")
            for (p in patterns) {
                appendLine()
                appendLine("[[highlights]]")
                appendLine("id = \"$p-id\"")
                appendLine("pattern = \"$p\"")
            }
        }

    // Write the way another instance would: a complete file swapped in atomically.
    private fun atomicWrite(
        path: Path,
        text: String,
    ) {
        val tmp = Path(path.parent ?: Path(configDir), path.name + ".ext.tmp")
        fs.sink(tmp).buffered().use { it.writeString(text) }
        fs.atomicMove(tmp, path)
    }

    @Test
    fun external_edit_is_reloaded_into_memory() =
        runBlocking {
            atomicWrite(globalFile(), globalToml("apple"))

            val store = CharacterConfigStore(configDir, fs)
            store.load()
            assertTrue(
                store
                    .current(GLOBAL_CHARACTER_ID)
                    .highlights
                    .single()
                    .pattern == "apple",
            )

            val watchScope = CoroutineScope(Dispatchers.IO + Job())
            store.startWatching(watchScope)
            delay(500) // let the watcher register its directories before we change the file

            // Another instance (or the user in a text editor) adds a highlight.
            atomicWrite(globalFile(), globalToml("apple", "banana"))

            try {
                withTimeout(15_000) {
                    while (store.current(GLOBAL_CHARACTER_ID).highlights.none { it.pattern == "banana" }) {
                        delay(100)
                    }
                }
            } finally {
                watchScope.cancel()
            }

            val patterns = store.current(GLOBAL_CHARACTER_ID).highlights.map { it.pattern }
            assertTrue("banana" in patterns, "external edit was not reloaded: $patterns")
        }
}
