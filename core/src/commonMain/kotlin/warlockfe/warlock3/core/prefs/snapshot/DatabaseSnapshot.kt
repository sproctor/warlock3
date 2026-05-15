package warlockfe.warlock3.core.prefs.snapshot

import co.touchlab.kermit.Logger
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path

private val logger = Logger.withTag("DatabaseSnapshot")

private val snapshotPattern = Regex("""^warlock-v(\d+)\.db$""")
private val sidecarSuffixes = listOf("-wal", "-shm")

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
 *  2. If the target snapshot file is missing, seed it from the newest existing older snapshot.
 *  3. Hand the target path to [buildDatabase], which is responsible for invoking the platform
 *     Room builder, registering migrations, and returning the built database.
 *  4. On migration failure, delete the target so the next launch may recover, then rethrow.
 */
fun <T> openVersionedDatabase(
    directory: Path,
    fileSystem: FileSystem,
    currentVersion: Int,
    buildDatabase: (databasePath: Path) -> T,
    legacyFileName: String,
): T {
    fileSystem.createDirectories(directory)

    migrateLegacyDatabase(directory, legacyFileName, currentVersion, fileSystem)

    val target = Path(directory, snapshotFileName(currentVersion))
    val seedSource: SnapshotInfo? =
        if (fileSystem.exists(target)) {
            null
        } else {
            val candidate = findSeedCandidate(listSnapshots(directory, fileSystem), currentVersion)
            when {
                candidate == null -> null
                else -> {
                    copySnapshot(candidate.path, target, fileSystem)
                    logger.i { "Seeded ${target.name} from ${candidate.path.name}" }
                    candidate
                }
            }
        }
    val targetExistedBeforeBuild = fileSystem.exists(target)

    val database =
        try {
            buildDatabase(target)
        } catch (t: Throwable) {
            logger.e(t) {
                val from = seedSource?.let { " (seeded from v${it.version})" } ?: ""
                "Migration to ${target.name} failed$from; deleting failed target so next launch may recover"
            }
            runCatching { fileSystem.delete(target, mustExist = false) }
            for (suffix in sidecarSuffixes) {
                runCatching { fileSystem.delete(Path(directory, target.name + suffix), mustExist = false) }
            }
            throw t
        }

    if (seedSource == null && targetExistedBeforeBuild) {
        logger.i { "Opened existing ${target.name} without seeding" }
    }

    return database
}

/**
 * Copy [source] to [target] (plus any -wal/-shm sidecars that exist next to [source]).
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
    val sourceParent = source.parent ?: Path(".")

    data class SidecarCopy(
        val source: Path,
        val tmp: Path,
        val target: Path,
    )
    val mainTmp = Path(targetParent, target.name + ".tmp")
    val sidecars =
        sidecarSuffixes.mapNotNull { suffix ->
            val src = Path(sourceParent, source.name + suffix)
            if (!fileSystem.exists(src)) return@mapNotNull null
            SidecarCopy(
                source = src,
                tmp = Path(targetParent, target.name + suffix + ".tmp"),
                target = Path(targetParent, target.name + suffix),
            )
        }

    fun cleanupTmps() {
        runCatching { fileSystem.delete(mainTmp, mustExist = false) }
        for (s in sidecars) runCatching { fileSystem.delete(s.tmp, mustExist = false) }
    }

    try {
        copyFile(source, mainTmp, fileSystem)
        for (s in sidecars) copyFile(s.source, s.tmp, fileSystem)

        if (fileSystem.exists(target)) {
            cleanupTmps()
            return
        }
        fileSystem.atomicMove(mainTmp, target)
        for (s in sidecars) {
            if (fileSystem.exists(s.target)) {
                fileSystem.delete(s.tmp, mustExist = false)
            } else {
                fileSystem.atomicMove(s.tmp, s.target)
            }
        }
    } catch (t: Throwable) {
        cleanupTmps()
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
) {
    val legacy = Path(directory, legacyFileName)
    if (!fileSystem.exists(legacy)) return
    if (listSnapshots(directory, fileSystem).isNotEmpty()) return

    val target = Path(directory, snapshotFileName(currentVersion))
    copySnapshot(legacy, target, fileSystem)
    logger.i { "Copied legacy $legacyFileName to ${target.name}" }
}
