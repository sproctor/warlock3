package warlockfe.warlock3.core.prefs.snapshot

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseSnapshotTest {
    private val fs = SystemFileSystem
    private lateinit var dir: Path

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("snapshot-test").toAbsolutePath().toString())
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private fun write(
        path: Path,
        contents: String,
    ) {
        fs.sink(path).buffered().use { it.writeString(contents) }
    }

    private fun read(path: Path): String = fs.source(path).buffered().use { it.readByteArray().decodeToString() }

    private fun snap(version: Int) = SnapshotInfo(version, Path(snapshotFileName(version)))

    // ---- pure logic ----

    @Test
    fun parsing_acceptsValidAndRejectsInvalid() {
        assertEquals(17, parseSnapshotVersion("warlock-v17.db"))
        assertEquals("warlock-v17.db", snapshotFileName(17))
        assertNull(parseSnapshotVersion("warlock.db"))
        assertNull(parseSnapshotVersion("warlock-v17.db.bak"))
        assertNull(parseSnapshotVersion("warlock-v17.db-wal"))
        assertNull(parseSnapshotVersion("prefs.db"))
    }

    @Test
    fun findSeedCandidate_picksHighestBelowCurrent() {
        assertNull(findSeedCandidate(emptyList(), 17))
        assertEquals(15, findSeedCandidate(listOf(snap(15)), 17)?.version)
        assertEquals(16, findSeedCandidate(listOf(snap(10), snap(12), snap(16)), 17)?.version)
        // Exact-current and above-current are excluded.
        assertEquals(14, findSeedCandidate(listOf(snap(14), snap(17), snap(20)), 17)?.version)
        assertNull(findSeedCandidate(listOf(snap(17), snap(20)), 17))
    }

    // ---- discovery + file ops ----

    @Test
    fun listSnapshots_filtersNonMatchingFiles() {
        write(Path(dir, "warlock-v10.db"), "v10")
        write(Path(dir, "warlock-v17.db"), "v17")
        write(Path(dir, "warlock-v17.db-wal"), "wal")
        write(Path(dir, "warlock-v10.db.bak"), "bak")
        write(Path(dir, "warlock.db"), "old")
        write(Path(dir, "prefs.db"), "legacy")

        val versions = listSnapshots(dir, fs).map { it.version }.toSet()
        assertEquals(setOf(10, 17), versions)
    }

    @Test
    fun copySnapshot_copiesMainAndSidecarsAtomically() {
        val source = Path(dir, "warlock-v10.db")
        write(source, "main")
        write(Path(dir, "warlock-v10.db-wal"), "wal")
        write(Path(dir, "warlock-v10.db-shm"), "shm")

        val target = Path(dir, "warlock-v17.db")
        copySnapshot(source, target, fs)

        assertEquals("main", read(target))
        assertEquals("wal", read(Path(dir, "warlock-v17.db-wal")))
        assertEquals("shm", read(Path(dir, "warlock-v17.db-shm")))
        assertFalse(fs.exists(Path(dir, "warlock-v17.db.tmp")))
        assertFalse(fs.exists(Path(dir, "warlock-v17.db-wal.tmp")))
        assertTrue(fs.exists(source))
    }

    @Test
    fun copySnapshot_skipsAbsentSidecarsAndPreservesExistingTarget() {
        write(Path(dir, "warlock-v10.db"), "fresh")
        copySnapshot(Path(dir, "warlock-v10.db"), Path(dir, "warlock-v17.db"), fs)
        assertTrue(fs.exists(Path(dir, "warlock-v17.db")))
        assertFalse(fs.exists(Path(dir, "warlock-v17.db-wal")))

        // Calling again with an existing target leaves the target untouched.
        write(Path(dir, "warlock-v17.db"), "existing")
        copySnapshot(Path(dir, "warlock-v10.db"), Path(dir, "warlock-v17.db"), fs)
        assertEquals("existing", read(Path(dir, "warlock-v17.db")))
    }

    // ---- end-to-end open flow ----

    private fun open(
        currentVersion: Int = 17,
        legacy: String = "prefs.db",
        build: (Path) -> Unit = { path -> if (!fs.exists(path)) write(path, "fresh") },
    ) = openVersionedDatabase(
        directory = dir,
        fileSystem = fs,
        currentVersion = currentVersion,
        legacyFileName = legacy,
        buildDatabase = build,
    )

    @Test
    fun freshInstall_createsTargetThroughBuilder() {
        open()
        assertEquals("fresh", read(Path(dir, "warlock-v17.db")))
    }

    @Test
    fun preexistingTarget_isOpenedAsIs() {
        write(Path(dir, "warlock-v17.db"), "existing")
        var saw: String? = null
        open { path -> saw = read(path) }
        assertEquals("existing", saw)
        assertEquals("existing", read(Path(dir, "warlock-v17.db")))
    }

    @Test
    fun seedsFromNewestOlderSnapshot() {
        write(Path(dir, "warlock-v10.db"), "v10-data")
        write(Path(dir, "warlock-v14.db"), "v14-data")
        var saw: String? = null
        open { path -> saw = read(path) }
        assertEquals("v14-data", saw)
        assertTrue(fs.exists(Path(dir, "warlock-v14.db")))
    }

    @Test
    fun legacyDatabase_isCopiedOnFirstOpenAndOriginalIsLeftInPlace() {
        write(Path(dir, "prefs.db"), "legacy-data")
        var saw: String? = null
        open { path -> saw = read(path) }
        assertEquals("legacy-data", saw)
        // Original prefs.db must stay so a pre-snapshot binary downgrade can still open it.
        assertTrue(fs.exists(Path(dir, "prefs.db")))
        assertEquals("legacy-data", read(Path(dir, "prefs.db")))
        assertEquals("legacy-data", read(Path(dir, "warlock-v17.db")))
    }

    @Test
    fun legacyDatabase_isIgnoredOnceVersionedSnapshotExists() {
        write(Path(dir, "prefs.db"), "legacy")
        write(Path(dir, "warlock-v10.db"), "v10")
        open()
        assertEquals("legacy", read(Path(dir, "prefs.db")))
        // v17 was seeded from v10, not from the legacy file.
        assertEquals("v10", read(Path(dir, "warlock-v17.db")))
    }

    @Test
    fun migrationFailure_deletesTargetAndPreservesSource() {
        write(Path(dir, "warlock-v10.db"), "v10")
        val failure =
            assertFails {
                open { _ -> throw IllegalStateException("migration boom") }
            }
        assertEquals("migration boom", failure.message)
        assertFalse(fs.exists(Path(dir, "warlock-v17.db")))
        assertEquals("v10", read(Path(dir, "warlock-v10.db")))
    }
}
