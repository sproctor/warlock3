package warlockfe.warlock3.core.mudmobile

import co.touchlab.kermit.Logger
import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.encodeToString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import warlockfe.warlock3.core.util.sha256Hex

/**
 * Two-way sync of Warlock's per-character settings (the `.toml` files under `characters/`) with the
 * user's MUD Mobile account, so highlights/macros/variables/layout/etc. follow them between machines.
 *
 * Only the `characters/` tree is synced. `client.toml` (which holds the MUD Mobile device token and
 * machine-local paths) and `connections.toml` deliberately stay local.
 *
 * Each file is identified by `hash = sha256_hex(raw bytes)`. The server does per-file compare-and-swap
 * on writes (keyed on a `baseHash`); this class keeps, per file, the **base hash** of the last version
 * it synced (in a local `.warlock-sync-state.toml`) and uses it to classify each file as unchanged,
 * locally-changed (push), remotely-changed (pull), or changed on both sides (a [SyncConflict] the user
 * resolves). Membership (adds/deletes across machines) is reconciled here; the server only does CAS.
 *
 * Pulled files are written straight to disk; the [warlockfe.warlock3.core.prefs.config.CharacterConfigStore]
 * file watcher then reloads them into the live config. The contract is documented in §4.5 of
 * docs/specs/mudmobile-integration.md.
 */
class WarlockSettingsSync(
    private val api: WarlockFilesApi,
    configDirectory: String,
    private val fileSystem: FileSystem,
    // Supplies the current device token (null when the user hasn't connected MUD Mobile).
    private val tokenProvider: suspend () -> String?,
    // Writes a pulled file through CharacterConfigStore so it takes the per-character file lock
    // (serializing with in-app edits) and updates the live in-memory config. Returns false when the
    // store doesn't own the path, in which case we fall back to a plain direct write. Defaults to
    // always-fallback (used in tests, where there is no store).
    private val writeThroughStore: suspend (Path, String) -> Boolean = { _, _ -> false },
) {
    private val logger = Logger.withTag("WarlockSettingsSync")
    private val toml =
        Toml {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val rootDir = Path(configDirectory)
    private val charactersDir = Path(rootDir, "characters")

    // Base-hash snapshot of what we last synced. Lives at the config root (NOT under characters/, so it
    // is never itself a synced file).
    private val statePath = Path(rootDir, ".warlock-sync-state.toml")

    private val mutex = Mutex()
    private val _state = MutableStateFlow(WarlockSyncUiState())
    val state: StateFlow<WarlockSyncUiState> = _state.asStateFlow()

    /**
     * Run a full two-way reconcile. Non-conflicting changes are applied automatically (pushed/pulled/
     * deleted); files changed on both sides are surfaced in [state] as [SyncConflict]s for the user to
     * resolve with [resolveConflict]. A no-op (and no error) when MUD Mobile isn't connected.
     */
    suspend fun sync() {
        mutex.withLock {
            val token = tokenProvider() ?: return
            _state.value = _state.value.copy(status = SyncStatus.Syncing, message = null)
            runCatching { reconcile(token) }
                .onFailure { e ->
                    logger.e(e) { "settings sync failed" }
                    _state.value = _state.value.copy(status = SyncStatus.Idle, message = "Sync failed: ${e.message}")
                }
        }
    }

    /** Apply the user's decision for a single conflicting file, then drop it from the conflict list. */
    suspend fun resolveConflict(
        path: String,
        resolution: ConflictResolution,
    ) {
        mutex.withLock {
            val token = tokenProvider() ?: return
            val conflict = _state.value.conflicts.firstOrNull { it.path == path } ?: return
            val base = readBaseHashes().toMutableMap()
            runCatching {
                when (resolution) {
                    ConflictResolution.KEEP_LOCAL -> {
                        if (conflict.localContent != null) {
                            forcePush(token, path, conflict.localContent, base)
                        } else {
                            // Local side deleted the file: delete it remotely too.
                            api.deleteWarlockFile(token, path)
                            base.remove(path)
                        }
                    }

                    ConflictResolution.TAKE_REMOTE -> {
                        if (conflict.remoteContent != null && conflict.remoteHash != null) {
                            writeLocal(path, conflict.remoteContent)
                            base[path] = conflict.remoteHash
                        } else {
                            // Remote side deleted the file: delete it locally too.
                            deleteLocal(path)
                            base.remove(path)
                        }
                    }
                }
                writeBaseHashes(base)
            }.onFailure { e ->
                logger.e(e) { "resolveConflict failed for $path" }
            }
            _state.value =
                _state.value.copy(conflicts = _state.value.conflicts.filterNot { it.path == path })
        }
    }

    /**
     * Defer all pending conflicts (the "Later" action): drops them from view without resolving. They
     * resurface on the next [sync] because the base hashes are unchanged.
     */
    fun clearConflicts() {
        _state.value = _state.value.copy(conflicts = emptyList())
    }

    // --- reconcile ------------------------------------------------------------------------------

    private suspend fun reconcile(token: String) {
        val base = readBaseHashes().toMutableMap()
        val local = listLocalFiles() // path -> raw bytes
        val localHashes = local.mapValues { sha256Hex(it.value) }

        val remoteList =
            when (val result = api.listWarlockFiles(token)) {
                is ListWarlockFilesResult.Success -> {
                    result.files
                }

                ListWarlockFilesResult.Unauthorized -> {
                    _state.value = _state.value.copy(status = SyncStatus.Idle, message = "MUD Mobile token rejected.")
                    return
                }

                is ListWarlockFilesResult.Error -> {
                    _state.value = _state.value.copy(status = SyncStatus.Idle, message = "Couldn't reach MUD Mobile: ${result.message}")
                    return
                }
            }
        val remoteHashes = remoteList.associate { it.path to it.hash }
        val remoteModified = remoteList.associate { it.path to it.modified }

        val conflicts = mutableListOf<SyncConflict>()
        var pushed = 0
        var pulled = 0
        var deleted = 0

        val allPaths = (base.keys + localHashes.keys + remoteHashes.keys).toSortedSet()
        for (path in allPaths) {
            val localHash = localHashes[path]
            val remoteHash = remoteHashes[path]
            val baseHash = base[path]

            // Already identical on both sides (or both gone): just record the agreed hash as the base.
            if (localHash == remoteHash) {
                if (localHash == null) base.remove(path) else base[path] = localHash
                continue
            }

            val localChanged = localHash != baseHash
            val remoteChanged = remoteHash != baseHash

            when {
                localChanged && !remoteChanged -> {
                    // Only local moved: push it (or delete remotely if it's gone locally).
                    if (localHash == null) {
                        if (api.deleteWarlockFile(token, path)) {
                            base.remove(path)
                            deleted++
                        }
                    } else {
                        val content = local.getValue(path).decodeToString()
                        when (val result = api.writeWarlockFile(token, path, content, baseHash, overwrite = false)) {
                            is WriteWarlockFileResult.Success -> {
                                base[path] = result.hash
                                pushed++
                            }

                            is WriteWarlockFileResult.Conflict -> {
                                conflicts += buildConflict(token, path, content, result.currentHash, result.modified)
                            }

                            WriteWarlockFileResult.Unauthorized -> {
                                return
                            }

                            is WriteWarlockFileResult.Error -> {
                                logger.w { "push $path failed: ${result.message}" }
                            }
                        }
                    }
                }

                remoteChanged && !localChanged -> {
                    // Only remote moved: pull it (or delete locally if it's gone remotely).
                    if (remoteHash == null) {
                        deleteLocal(path)
                        base.remove(path)
                        deleted++
                    } else {
                        when (val result = api.readWarlockFile(token, path)) {
                            is ReadWarlockFileResult.Success -> {
                                writeLocal(path, result.content)
                                base[path] = result.hash
                                pulled++
                            }

                            ReadWarlockFileResult.NotFound -> {
                                base.remove(path)
                            }

                            ReadWarlockFileResult.Unauthorized -> {
                                return
                            }

                            is ReadWarlockFileResult.Error -> {
                                logger.w { "pull $path failed: ${result.message}" }
                            }
                        }
                    }
                }

                else -> {
                    // Changed on both sides to different content: needs the user.
                    val localContent = local[path]?.decodeToString()
                    val remoteContent = fetchRemoteContent(token, path, remoteHash)
                    conflicts +=
                        SyncConflict(
                            path = path,
                            localContent = localContent,
                            remoteContent = remoteContent,
                            remoteHash = remoteHash,
                            remoteModified = remoteModified[path],
                        )
                }
            }
        }

        writeBaseHashes(base)
        _state.value =
            _state.value.copy(
                status = SyncStatus.Idle,
                conflicts = conflicts,
                message = summaryMessage(pushed, pulled, deleted, conflicts.size),
            )
    }

    private suspend fun buildConflict(
        token: String,
        path: String,
        localContent: String,
        remoteHash: String?,
        remoteModified: String?,
    ): SyncConflict =
        SyncConflict(
            path = path,
            localContent = localContent,
            remoteContent = fetchRemoteContent(token, path, remoteHash),
            remoteHash = remoteHash,
            remoteModified = remoteModified,
        )

    private suspend fun fetchRemoteContent(
        token: String,
        path: String,
        remoteHash: String?,
    ): String? {
        if (remoteHash == null) return null
        return when (val result = api.readWarlockFile(token, path)) {
            is ReadWarlockFileResult.Success -> result.content
            else -> null
        }
    }

    private suspend fun forcePush(
        token: String,
        path: String,
        content: String,
        base: MutableMap<String, String>,
    ) {
        when (val result = api.writeWarlockFile(token, path, content, baseHash = null, overwrite = true)) {
            is WriteWarlockFileResult.Success -> base[path] = result.hash
            else -> logger.w { "force push $path failed: $result" }
        }
    }

    private fun summaryMessage(
        pushed: Int,
        pulled: Int,
        deleted: Int,
        conflicts: Int,
    ): String {
        if (pushed == 0 && pulled == 0 && deleted == 0 && conflicts == 0) return "Settings up to date."
        val parts = mutableListOf<String>()
        if (pushed > 0) parts += "$pushed uploaded"
        if (pulled > 0) parts += "$pulled downloaded"
        if (deleted > 0) parts += "$deleted removed"
        if (conflicts > 0) parts += "$conflicts need review"
        return parts.joinToString(", ")
    }

    // --- local file IO --------------------------------------------------------------------------

    // Walk characters/ and return each *.toml file keyed by its forward-slash relative path from the
    // config root (e.g. "characters/gs4/tholan/highlights.toml").
    private fun listLocalFiles(): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        if (fileSystem.metadataOrNull(charactersDir)?.isDirectory != true) return result

        fun walk(dir: Path) {
            for (entry in fileSystem.list(dir)) {
                val meta = fileSystem.metadataOrNull(entry) ?: continue
                if (meta.isDirectory) {
                    walk(entry)
                } else if (entry.name.endsWith(".toml")) {
                    runCatching {
                        val bytes = fileSystem.source(entry).buffered().use { it.readByteArray() }
                        result[relativePath(entry)] = bytes
                    }.onFailure { logger.w(it) { "couldn't read $entry" } }
                }
            }
        }
        walk(charactersDir)
        return result
    }

    private fun relativePath(path: Path): String {
        val full = path.toString()
        val root = rootDir.toString().trimEnd('/', '\\')
        val rel = full.removePrefix(root).trimStart('/', '\\')
        return rel.replace('\\', '/')
    }

    private fun resolveLocal(relativePath: String): Path {
        var path = rootDir
        for (segment in relativePath.split('/')) {
            if (segment.isNotEmpty()) path = Path(path, segment)
        }
        return path
    }

    private suspend fun writeLocal(
        relativePath: String,
        content: String,
    ) {
        val target = resolveLocal(relativePath)
        // Prefer writing through CharacterConfigStore so the write takes the per-character file lock
        // (blocking concurrent in-app edits) and refreshes the in-memory config.
        if (writeThroughStore(target, content)) return
        val parent = target.parent
        if (parent != null && fileSystem.metadataOrNull(parent) == null) {
            fileSystem.createDirectories(parent)
        }
        val tmp = Path(parent ?: rootDir, target.name + ".synctmp")
        fileSystem.sink(tmp).buffered().use { it.writeString(content) }
        fileSystem.atomicMove(tmp, target)
    }

    private fun deleteLocal(relativePath: String) {
        val target = resolveLocal(relativePath)
        runCatching {
            if (fileSystem.metadataOrNull(target) != null) fileSystem.delete(target)
        }.onFailure { logger.w(it) { "couldn't delete $target" } }
    }

    // --- base-hash state file -------------------------------------------------------------------

    private fun readBaseHashes(): Map<String, String> {
        if (fileSystem.metadataOrNull(statePath) == null) return emptyMap()
        return runCatching {
            val text =
                fileSystem
                    .source(statePath)
                    .buffered()
                    .use { it.readByteArray() }
                    .decodeToString()
            val element = toml.parseToTomlTable(text)
            toml
                .decodeFromTomlElement(SyncStateFile.serializer(), element)
                .files
                .associate { it.path to it.baseHash }
        }.getOrElse {
            logger.w(it) { "couldn't read sync state; treating as empty" }
            emptyMap()
        }
    }

    private fun writeBaseHashes(hashes: Map<String, String>) {
        runCatching {
            if (fileSystem.metadataOrNull(rootDir) == null) fileSystem.createDirectories(rootDir)
            val file = SyncStateFile(hashes.toSortedMap().map { SyncStateEntry(it.key, it.value) })
            val text = toml.encodeToString(SyncStateFile.serializer(), file)
            val tmp = Path(rootDir, statePath.name + ".tmp")
            fileSystem.sink(tmp).buffered().use { it.writeString(text) }
            fileSystem.atomicMove(tmp, statePath)
        }.onFailure { logger.e(it) { "couldn't write sync state" } }
    }
}

/** UI-facing sync state. */
data class WarlockSyncUiState(
    val status: SyncStatus = SyncStatus.Idle,
    val message: String? = null,
    val conflicts: List<SyncConflict> = emptyList(),
)

enum class SyncStatus {
    Idle,
    Syncing,
}

/** A single file that changed on both sides; [localContent]/[remoteContent] are null when that side deleted it. */
data class SyncConflict(
    val path: String,
    val localContent: String?,
    val remoteContent: String?,
    val remoteHash: String?,
    val remoteModified: String?,
)

enum class ConflictResolution {
    KEEP_LOCAL,
    TAKE_REMOTE,
}

@Serializable
internal data class SyncStateFile(
    val files: List<SyncStateEntry> = emptyList(),
)

@Serializable
internal data class SyncStateEntry(
    val path: String,
    val baseHash: String,
)
