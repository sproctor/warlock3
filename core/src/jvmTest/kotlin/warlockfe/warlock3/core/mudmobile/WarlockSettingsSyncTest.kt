package warlockfe.warlock3.core.mudmobile

import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import org.junit.Test
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.util.sha256Hex
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WarlockSettingsSyncTest {
    private val fs = SystemFileSystem
    private val root = Path(Files.createTempDirectory("sync-test").toAbsolutePath().toString())
    private val api = FakeFilesApi()
    private val sync =
        WarlockSettingsSync(
            api = api,
            configDirectory = root.toString(),
            fileSystem = fs,
            tokenProvider = { "wlk_test" },
        )

    private val highlightsPath = "characters/gs4/tholan/highlights.toml"

    @AfterTest
    fun cleanup() {
        runCatching {
            Files
                .walk(
                    java.nio.file.Path
                        .of(root.toString()),
                ).sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `pushes a new local file to the remote`() =
        runBlocking {
            writeLocal(highlightsPath, "highlights = []\n")

            sync.sync()

            assertEquals(SyncStatus.Idle, sync.state.value.status)
            assertTrue(
                sync.state.value.conflicts
                    .isEmpty(),
            )
            assertEquals("highlights = []\n", api.files[highlightsPath]?.content)
        }

    @Test
    fun `pulls a remote-only file to disk`() =
        runBlocking {
            api.put(highlightsPath, "highlights = [\"remote\"]\n")

            sync.sync()

            assertEquals("highlights = [\"remote\"]\n", readLocal(highlightsPath))
        }

    @Test
    fun `second sync with no changes is a no-op`() =
        runBlocking {
            writeLocal(highlightsPath, "highlights = []\n")
            sync.sync()
            val writesAfterFirst = api.writeCount

            sync.sync()

            assertEquals(writesAfterFirst, api.writeCount, "no extra writes on a clean re-sync")
            assertEquals("Settings up to date.", sync.state.value.message)
        }

    @Test
    fun `divergent edits on both sides produce a conflict`() =
        runBlocking {
            // First sync establishes a shared base.
            writeLocal(highlightsPath, "base\n")
            sync.sync()
            // Now both sides edit to different content.
            writeLocal(highlightsPath, "local-edit\n")
            api.put(highlightsPath, "remote-edit\n")

            sync.sync()

            val conflicts = sync.state.value.conflicts
            assertEquals(1, conflicts.size)
            assertEquals("local-edit\n", conflicts[0].localContent)
            assertEquals("remote-edit\n", conflicts[0].remoteContent)
        }

    @Test
    fun `resolving keep-local overwrites the remote`() =
        runBlocking {
            writeLocal(highlightsPath, "base\n")
            sync.sync()
            writeLocal(highlightsPath, "local-edit\n")
            api.put(highlightsPath, "remote-edit\n")
            sync.sync()

            sync.resolveConflict(highlightsPath, ConflictResolution.KEEP_LOCAL)

            assertEquals("local-edit\n", api.files[highlightsPath]?.content)
            assertTrue(
                sync.state.value.conflicts
                    .isEmpty(),
            )
        }

    @Test
    fun `resolving take-remote overwrites the local file`() =
        runBlocking {
            writeLocal(highlightsPath, "base\n")
            sync.sync()
            writeLocal(highlightsPath, "local-edit\n")
            api.put(highlightsPath, "remote-edit\n")
            sync.sync()

            sync.resolveConflict(highlightsPath, ConflictResolution.TAKE_REMOTE)

            assertEquals("remote-edit\n", readLocal(highlightsPath))
            assertTrue(
                sync.state.value.conflicts
                    .isEmpty(),
            )
        }

    @Test
    fun `a remote deletion removes the local file`() =
        runBlocking {
            writeLocal(highlightsPath, "base\n")
            sync.sync() // base established, remote now has it
            api.deleteWarlockFile("wlk_test", highlightsPath) // deleted remotely

            sync.sync()

            assertNull(readLocal(highlightsPath))
        }

    @Test
    fun `pull writes through the config store and updates in-memory config`() =
        runBlocking {
            val store = CharacterConfigStore(root.toString(), fs)
            store.load()
            val syncWithStore =
                WarlockSettingsSync(
                    api = api,
                    configDirectory = root.toString(),
                    fileSystem = fs,
                    tokenProvider = { "wlk_test" },
                    writeThroughStore = { path, content -> store.writeSectionFile(path, content) },
                )
            api.put("characters/gs4/tholan/variables.toml", "[variables]\nfoo = \"bar\"\n")

            syncWithStore.sync()

            assertEquals("bar", store.current("gs4:tholan").variables["foo"])
        }

    private fun writeLocal(
        rel: String,
        content: String,
    ) {
        var path = root
        for (segment in rel.split('/')) path = Path(path, segment)
        path.parent?.let { if (fs.metadataOrNull(it) == null) fs.createDirectories(it) }
        fs.sink(path).buffered().use { it.writeString(content) }
    }

    private fun readLocal(rel: String): String? {
        var path = root
        for (segment in rel.split('/')) path = Path(path, segment)
        if (fs.metadataOrNull(path) == null) return null
        return fs.source(path).buffered().use { it.readString() }
    }
}

/** In-memory [WarlockFilesApi] with compare-and-swap semantics matching the server contract. */
private class FakeFilesApi : WarlockFilesApi {
    data class Stored(
        val content: String,
        val hash: String,
    )

    val files = mutableMapOf<String, Stored>()
    var writeCount = 0

    fun put(
        path: String,
        content: String,
    ) {
        files[path] = Stored(content, sha256Hex(content))
    }

    override suspend fun listWarlockFiles(token: String): ListWarlockFilesResult =
        ListWarlockFilesResult.Success(files.map { WarlockFileMeta(path = it.key, hash = it.value.hash) })

    override suspend fun readWarlockFile(
        token: String,
        path: String,
    ): ReadWarlockFileResult {
        val stored = files[path] ?: return ReadWarlockFileResult.NotFound
        return ReadWarlockFileResult.Success(stored.content, stored.hash, null)
    }

    override suspend fun writeWarlockFile(
        token: String,
        path: String,
        content: String,
        baseHash: String?,
        overwrite: Boolean,
    ): WriteWarlockFileResult {
        val current = files[path]
        if (!overwrite) {
            // create-only when baseHash is null; otherwise CAS against the current hash.
            if (baseHash == null && current != null) {
                return WriteWarlockFileResult.Conflict(current.hash, null)
            }
            if (baseHash != null && current?.hash != baseHash) {
                return WriteWarlockFileResult.Conflict(current?.hash, null)
            }
        }
        val hash = sha256Hex(content)
        files[path] = Stored(content, hash)
        writeCount++
        return WriteWarlockFileResult.Success(hash, null)
    }

    override suspend fun deleteWarlockFile(
        token: String,
        path: String,
    ): Boolean {
        files.remove(path)
        return true
    }
}
