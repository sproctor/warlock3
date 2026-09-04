package warlockfe.warlock3.core.prefs.config

import co.touchlab.kermit.Logger
import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlElement
import dev.eav.tomlkt.TomlTable
import dev.eav.tomlkt.encodeToString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.KSerializer
import warlockfe.warlock3.core.prefs.SettingsProblem
import warlockfe.warlock3.core.prefs.SettingsUnreadableException
import warlockfe.warlock3.core.prefs.describeForUser

/**
 * The file-persistence core shared by the config stores, for a single TOML file holding one
 * top-level [T]. Mirrors [CharacterConfigStore]'s per-file behavior: an in-memory [StateFlow] source
 * of truth, atomic temp-file + move writes guarded by a cross-process [withFileLock], a parsed
 * template carried forward so hand-written comments survive a rewrite, and lock-guarded
 * read-modify-write so a concurrent write from another app instance isn't clobbered.
 *
 * [CharacterConfigStore] keeps its own map-of-files implementation (it manages many files with
 * dynamically derived paths); this type backs the fixed-path singleton files (`client.toml`,
 * `connections.toml`).
 */
internal class TomlFileStore<T>(
    private val path: Path,
    private val fileSystem: FileSystem,
    private val serializer: KSerializer<T>,
    private val toml: Toml,
    private val default: T,
    // For arrays of tables, lets comments follow an entry across reorders by matching on `id`.
    private val elementKey: (TomlElement) -> Any? = CONFIG_ELEMENT_KEY,
    // Fills in any missing ids etc.; returns the normalized value and whether anything changed.
    private val normalize: (T) -> Pair<T, Boolean> = { it to false },
) {
    private val writeMutex = Mutex()
    private val state = MutableStateFlow(default)
    private var template: TomlElement? = null

    // A StateFlow already conflates and only emits distinct values, so it's returned as-is.
    fun observe(): Flow<T> = state

    fun current(): T = state.value

    suspend fun load() {
        val read = readFile(failOnUnreadable = true) ?: return
        template = read.first
        val (normalized, changed) = normalize(read.second)
        state.value = normalized
        if (changed) {
            writeMutex.withLock {
                fileSystem.ensureParentDir(path)
                withFileLock(lockFile()) { persist(normalized) }
            }
        }
    }

    suspend fun mutate(transform: (T) -> T) {
        writeMutex.withLock {
            fileSystem.ensureParentDir(path)
            withFileLock(lockFile()) {
                val onDisk = readFile()
                if (onDisk != null) template = onDisk.first
                val base = onDisk?.second ?: state.value
                val updated = transform(base)
                state.value = updated
                persist(updated)
            }
        }
    }

    /** True when [changed] is this store's file, so a watcher can dispatch reloads. */
    fun owns(changed: Path): Boolean = changed == path

    /** Reload after an out-of-band edit; skips when on-disk content already matches memory. */
    suspend fun reloadIfChanged() {
        writeMutex.withLock {
            val read = readFile() ?: return@withLock
            if (state.value != read.second) {
                template = read.first
                state.value = read.second
            }
        }
    }

    /**
     * The file's contents, or null when there is no file. [failOnUnreadable] is what the startup
     * read passes: a file that is there but cannot be read must not be mistaken for one that is not
     * there, because that reads as a fresh install and the next save writes defaults over it (see
     * [SettingsUnreadableException]). The write path has no launch left to abort, so it keeps the
     * older log-and-carry-on behavior.
     */
    private fun readFile(failOnUnreadable: Boolean = false): Pair<TomlTable, T>? {
        return runCatching {
            // Absence is decided by the parent's listing, not by stat'ing the file: see
            // [readTextOrNullIfAbsent]. A file that is there but unreadable throws out of here, as
            // does a parent directory that is there but could not be listed.
            val text = fileSystem.readTextOrNullIfAbsent(path, failOnUnreadable) ?: return null
            val element = toml.parseToTomlTable(text)
            element to toml.decodeFromTomlElement(serializer, element)
        }.onFailure {
            if (failOnUnreadable) {
                throw SettingsUnreadableException(
                    SettingsProblem.unreadable(
                        what = path.name,
                        reason = it.describeForUser(),
                        settingsLocation = (path.parent ?: path).toString(),
                    ),
                )
            }
            Logger.e(it) { "Failed to read config file $path; ignoring it" }
        }.getOrNull()
    }

    private fun persist(config: T) {
        runCatching {
            fileSystem.ensureParentDir(path)
            val text = toml.encodeWithTemplate(serializer, config, template, elementKey)
            fileSystem.writeTextAtomically(path, text)
            template = toml.parseToTomlTable(text)
        }.onFailure {
            Logger.e(it) { "Failed to write config file $path" }
        }
    }

    private fun lockFile(): Path = Path(path.parent ?: path, path.name + ".lock")
}
