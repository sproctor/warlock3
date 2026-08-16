package warlockfe.warlock3.core.prefs.snapshot

import co.touchlab.kermit.Logger
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import warlockfe.warlock3.core.prefs.config.tryWithFileLock

private val logger = Logger.withTag("DatabaseSnapshot")

private val snapshotPattern = Regex("""^warlock-v(\d+)\.db$""")
private val sidecarSuffixes = listOf("-wal", "-shm")

/**
 * Suffix of the lock file Room takes around opening and migrating a database (its `FileLock` locks
 * "not on the file itself but on a temporary file created with the same path but ending with
 * `.lck`"). Sharing it is what lets snapshot recovery serialize against a migration in another
 * process. If Room ever changes the convention this silently stops coordinating, which costs the
 * extra safety but leaves behaviour no worse than not locking at all.
 */
private const val ROOM_LOCK_SUFFIX = ".lck"

data class SnapshotInfo(
    val version: Int,
    val path: Path,
)

fun snapshotFileName(version: Int): String = "warlock-v$version.db"

fun parseSnapshotVersion(fileName: String): Int? =
    snapshotPattern
        .matchEntire(fileName)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()

fun listSnapshots(
    directory: Path,
    fileSystem: FileSystem,
): List<SnapshotInfo> {
    if (fileSystem.metadataOrNull(directory)?.isDirectory != true) return emptyList()
    return fileSystem
        .list(directory)
        .mapNotNull { path ->
            val version = parseSnapshotVersion(path.name) ?: return@mapNotNull null
            SnapshotInfo(version, path)
        }.sortedBy { it.version }
}

fun findSeedCandidate(
    snapshots: List<SnapshotInfo>,
    currentVersion: Int,
): SnapshotInfo? =
    snapshots
        .asSequence()
        .filter { it.version < currentVersion }
        .maxByOrNull { it.version }

/**
 * Open a Room-style database while honoring the versioned-snapshot strategy.
 *
 *  1. One-time rename of the legacy single-file database (if [legacyFileName] is provided)
 *     to `warlock-v<currentVersion>.db` when no versioned snapshot exists yet.
 *  2. Discard a target left behind by a launch whose migration never completed, so step 3 seeds it
 *     again instead of mistaking it for a healthy database (see [discardIncompleteTarget]).
 *  3. If the target snapshot file is missing, seed it from the newest existing older snapshot.
 *  4. Hand the target path to [buildDatabase], which is responsible for invoking the platform
 *     Room builder, registering migrations, and returning the built database.
 *  5. If [buildDatabase] itself throws, delete the target so the next launch re-seeds, then
 *     rethrow. Note that a *migration* failure does not land here: Room's `build()` is lazy, so
 *     migrations run (and fail) at the first query, long after this function has returned. Step 2
 *     is what recovers from that case, on the launch after it happens.
 *
 * [checkpoint] is invoked on a source database immediately before it is copied. It must fold any
 * write-ahead log into the main `.db` file (e.g. `PRAGMA wal_checkpoint(TRUNCATE)`) so that copying
 * the main file alone captures all committed data. This is required because only the main file is
 * copied -- copying the `-wal`/`-shm` sidecars to a new path is unsafe and can drop recent writes.
 *
 * [readSchemaVersion] reads a database file's `PRAGMA user_version`, returning null when the file
 * cannot be read. It drives step 2; the default leaves that check disabled.
 */
fun <T> openVersionedDatabase(
    directory: Path,
    fileSystem: FileSystem,
    currentVersion: Int,
    buildDatabase: (databasePath: Path) -> T,
    legacyFileName: String,
    checkpoint: (databasePath: Path) -> Unit = {},
    readSchemaVersion: (databasePath: Path) -> Int? = { null },
): T {
    fileSystem.createDirectories(directory)

    migrateLegacyDatabase(directory, legacyFileName, currentVersion, fileSystem, checkpoint)

    val target = Path(directory, snapshotFileName(currentVersion))

    // Settle the target's fate under the very lock Room uses, because discarding it means unlinking
    // a file another instance may be midway through migrating -- that instance would go on writing
    // to an unlinked database and its settings would vanish. Room takes a cross-process lock on
    // "<database>.lck" around the connection open that runs migrations
    // (RoomConnectionManager.openLocked, with useFileLock set while the database is unconfigured),
    // so holding it here gives us the only two outcomes we can live with: either a peer is migrating
    // and we block until it is done -- after which the target reports the current schema and is left
    // alone -- or no peer can begin until we have finished.
    var seedSource: SnapshotInfo? = null
    val locked =
        tryWithFileLock(Path(directory, target.name + ROOM_LOCK_SUFFIX)) {
            discardIncompleteTarget(directory, target, currentVersion, fileSystem, readSchemaVersion)
            seedSource = seedTargetIfMissing(directory, target, currentVersion, fileSystem, checkpoint)
        }
    if (!locked) {
        // No lock means no way to tell a stale target from one being migrated right now, so the
        // destructive half is skipped entirely. Seeding still runs: it only writes when there is
        // nothing there to destroy. A stale target is left for a launch that can take the lock.
        logger.w {
            "Could not lock ${target.name} for recovery; leaving any incomplete target in place this launch"
        }
        seedSource = seedTargetIfMissing(directory, target, currentVersion, fileSystem, checkpoint)
    }
    val targetExistedBeforeBuild = fileSystem.exists(target)

    val database =
        try {
            buildDatabase(target)
        } catch (t: Throwable) {
            logger.e(t) {
                val from = seedSource?.let { " (seeded from v${it.version})" } ?: ""
                "Building ${target.name} failed$from; deleting failed target so next launch may recover"
            }
            deleteDatabaseFiles(target, fileSystem)
            throw t
        }

    if (seedSource == null && targetExistedBeforeBuild) {
        logger.i { "Opened existing ${target.name} without seeding" }
    }

    return database
}

/**
 * Copy the newest older snapshot into place when the target is missing, returning the snapshot it
 * was seeded from (null when the target was already there, or when there is nothing to seed from and
 * the platform builder will create it from scratch).
 */
private fun seedTargetIfMissing(
    directory: Path,
    target: Path,
    currentVersion: Int,
    fileSystem: FileSystem,
    checkpoint: (databasePath: Path) -> Unit,
): SnapshotInfo? {
    if (fileSystem.exists(target)) return null
    val candidate = findSeedCandidate(listSnapshots(directory, fileSystem), currentVersion) ?: return null
    checkpoint(candidate.path)
    copySnapshot(candidate.path, target, fileSystem)
    logger.i { "Seeded ${target.name} from ${candidate.path.name}" }
    return candidate
}

/**
 * Discard a target that exists but still reports a schema older than [currentVersion].
 *
 * Room builds lazily and runs migrations in a transaction at the first query, so a failed migration
 * rolls back and leaves the seeded copy sitting at its source's version. Nothing about that file
 * distinguishes it from a healthy database on later launches: it exists, so it is never re-seeded,
 * and [findSeedCandidate] would even prefer it over the older snapshot it was copied from -- which
 * by then may have moved on, if the user went back to the previous build in the meantime.
 * Discarding it puts the next launch back on the seeding path with current data.
 *
 * Deleting it loses nothing: the app never opened it successfully, so it holds no writes the seed
 * source doesn't. It is kept anyway when there is no older snapshot to re-seed from, since then it
 * is the only copy of the user's settings and a stale version is no reason to throw them away.
 */
private fun discardIncompleteTarget(
    directory: Path,
    target: Path,
    currentVersion: Int,
    fileSystem: FileSystem,
    readSchemaVersion: (databasePath: Path) -> Int?,
) {
    if (!fileSystem.exists(target)) return
    val version = readSchemaVersion(target) ?: return
    if (version >= currentVersion) return

    val candidate = findSeedCandidate(listSnapshots(directory, fileSystem), currentVersion)
    if (candidate == null) {
        logger.w {
            "${target.name} is still at schema v$version, so an earlier migration never completed, " +
                "but there is no older snapshot to re-seed from; keeping it and migrating it again"
        }
        return
    }
    logger.w {
        "Discarding ${target.name}: still at schema v$version, so an earlier migration never " +
            "completed; re-seeding from ${candidate.path.name}"
    }
    deleteDatabaseFiles(target, fileSystem)
}

/** Best-effort delete of a database file along with any `-wal`/`-shm` sidecars beside it. */
private fun deleteDatabaseFiles(
    path: Path,
    fileSystem: FileSystem,
) {
    val parent = path.parent ?: Path(".")
    runCatching { fileSystem.delete(path, mustExist = false) }
    for (suffix in sidecarSuffixes) {
        runCatching { fileSystem.delete(Path(parent, path.name + suffix), mustExist = false) }
    }
}

/**
 * Copy the main `.db` file of [source] to [target]. The `-wal`/`-shm` sidecars are deliberately
 * NOT copied: a `-shm` is shared-memory coordination state and a `-wal` copied to a new path can
 * be ignored or mis-replayed by SQLite, reverting the database to a pre-checkpoint state and
 * silently dropping recent writes. Callers must checkpoint [source] first so the main file holds
 * all committed data (see [openVersionedDatabase]).
 *
 * Any stale `-wal`/`-shm` left next to [target] is removed after the copy so it cannot be applied
 * on top of the freshly seeded, self-contained main file.
 *
 * The copy is atomic-ish: bytes are written to a `<target>.tmp` companion first and only renamed
 * into place once the write completes. If [target] appears between the existence check and the
 * rename (concurrent process), the temp file is discarded and no overwrite happens.
 */
fun copySnapshot(
    source: Path,
    target: Path,
    fileSystem: FileSystem,
) {
    require(fileSystem.exists(source)) { "Source snapshot does not exist: $source" }
    if (fileSystem.exists(target)) return // another process / earlier step won

    val targetParent = target.parent ?: Path(".")
    val mainTmp = Path(targetParent, target.name + ".tmp")

    try {
        copyFile(source, mainTmp, fileSystem)

        if (fileSystem.exists(target)) {
            runCatching { fileSystem.delete(mainTmp, mustExist = false) }
            return
        }
        // Drop any leftover sidecars BEFORE the main file lands, so there is never a moment where
        // the freshly copied main coexists with a stale -wal/-shm that SQLite could mis-apply.
        for (suffix in sidecarSuffixes) {
            runCatching { fileSystem.delete(Path(targetParent, target.name + suffix), mustExist = false) }
        }
        fileSystem.atomicMove(mainTmp, target)
    } catch (t: Throwable) {
        runCatching { fileSystem.delete(mainTmp, mustExist = false) }
        throw t
    }
}

private fun copyFile(
    source: Path,
    target: Path,
    fileSystem: FileSystem,
) {
    fileSystem.source(source).buffered().use { src ->
        fileSystem.sink(target).buffered().use { sink ->
            src.transferTo(sink)
        }
    }
}

/**
 * One-time migration: if a legacy single-file database exists in [directory] and no versioned
 * snapshot exists yet, copy it (and its sidecars) to the versioned name for [currentVersion].
 *
 * The legacy file is left in place so an older binary that downgrades to a pre-snapshot
 * version can still open it. Once any versioned snapshot exists this is a no-op.
 */
private fun migrateLegacyDatabase(
    directory: Path,
    legacyFileName: String,
    currentVersion: Int,
    fileSystem: FileSystem,
    checkpoint: (databasePath: Path) -> Unit,
) {
    val legacy = Path(directory, legacyFileName)
    if (!fileSystem.exists(legacy)) return
    if (listSnapshots(directory, fileSystem).isNotEmpty()) return

    val target = Path(directory, snapshotFileName(currentVersion))
    checkpoint(legacy)
    copySnapshot(legacy, target, fileSystem)
    logger.i { "Copied legacy $legacyFileName to ${target.name}" }
}
