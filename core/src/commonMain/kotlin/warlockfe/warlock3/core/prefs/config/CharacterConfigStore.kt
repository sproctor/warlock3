package warlockfe.warlock3.core.prefs.config

import co.touchlab.kermit.Logger
import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlElement
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlTable
import dev.eav.tomlkt.encodeToString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.uuid.Uuid

const val GLOBAL_CHARACTER_ID = "global"

/**
 * Human-editable, file-backed store for highlights, names, and variables. Each character gets a
 * single TOML file holding all three sections; global highlights/names live in `global.toml`.
 *
 * The store keeps every config in memory as the single source of truth (so reads are reactive and
 * cheap) and persists whole files atomically on every mutation. Because the three features share
 * one file per character, the Highlight/Name/Variable repositories all funnel through this store so
 * their writes never clobber each other's sections.
 */
class CharacterConfigStore(
    configDirectory: String,
    private val fileSystem: FileSystem,
) {
    private val rootDir = Path(configDirectory)
    private val charactersDir = Path(rootDir, "characters")
    private val globalFile = Path(rootDir, "$GLOBAL_CHARACTER_ID.toml")

    private val toml =
        Toml {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val writeMutex = Mutex()
    private val state = MutableStateFlow<Map<String, CharacterConfig>>(emptyMap())

    // The last-parsed TOML document for each character, used as the comment/formatting template when
    // we re-encode on save so hand-written comments survive the rewrite. Only touched during [load]
    // and on the write path (under [writeMutex]).
    private val templates = mutableMapOf<String, TomlElement>()

    /** Exposed for tests/diagnostics; observers should prefer [observe]. */
    val configs: StateFlow<Map<String, CharacterConfig>> = state

    fun observe(characterId: String): Flow<CharacterConfig> =
        state
            .map { it[characterId] ?: CharacterConfig(character = characterId) }
            .distinctUntilChanged()

    fun current(characterId: String): CharacterConfig = state.value[characterId] ?: CharacterConfig(character = characterId)

    fun snapshot(): Map<String, CharacterConfig> = state.value

    /**
     * Reads every config file from disk into memory. Hand-authored entries missing an `id` are
     * assigned one and the file is rewritten so the id stays stable across launches. Safe to call
     * before any data exists (results in an empty store).
     */
    suspend fun load() {
        val loaded = mutableMapOf<String, CharacterConfig>()
        for (path in listConfigFiles()) {
            val (element, config) = readConfig(path) ?: continue
            val key = config.character.ifEmpty { characterIdFromPath(path) }
            loaded[key] = config.copy(character = key)
            templates[key] = element
        }
        // Fill in any missing ids, persisting only the files we actually changed.
        val normalized = mutableMapOf<String, CharacterConfig>()
        val changed = mutableSetOf<String>()
        for ((key, config) in loaded) {
            val (fixed, didChange) = config.withGeneratedIds()
            normalized[key] = fixed
            if (didChange) changed += key
        }
        state.value = normalized
        for (key in changed) {
            val target = pathForCharacter(key)
            writeMutex.withLock {
                ensureParentDir(target)
                withFileLock(lockFileFor(target)) { persist(key, normalized.getValue(key)) }
            }
        }
    }

    suspend fun mutate(
        characterId: String,
        transform: (CharacterConfig) -> CharacterConfig,
    ) {
        val target = pathForCharacter(characterId)
        writeMutex.withLock {
            ensureParentDir(target)
            withFileLock(lockFileFor(target)) {
                // Read-modify-write against the current on-disk file so a concurrent write from
                // another app instance isn't clobbered: re-read inside the lock, apply the transform
                // to that, then persist. The transform (an upsert / move / delete) composes onto
                // whatever the other process last wrote, instead of overwriting it with a stale
                // in-memory snapshot.
                val onDisk = readConfig(target)
                if (onDisk != null) templates[characterId] = onDisk.first
                val base =
                    (onDisk?.second ?: state.value[characterId] ?: CharacterConfig(character = characterId))
                        .copy(character = characterId)
                val updated = transform(base).copy(character = characterId)
                state.value = state.value + (characterId to updated)
                persist(characterId, updated)
            }
        }
    }

    private fun listConfigFiles(): List<Path> {
        val files = mutableListOf<Path>()
        if (fileSystem.metadataOrNull(globalFile) != null) {
            files += globalFile
        }
        if (fileSystem.metadataOrNull(charactersDir)?.isDirectory == true) {
            // Files live one level down, in characters/<gameCode>/<name>.toml. Tolerate a stray
            // .toml directly under characters/ as well.
            for (entry in fileSystem.list(charactersDir)) {
                if (fileSystem.metadataOrNull(entry)?.isDirectory == true) {
                    files += fileSystem.list(entry).filter { it.name.endsWith(".toml") }
                } else if (entry.name.endsWith(".toml")) {
                    files += entry
                }
            }
        }
        return files
    }

    // Parse to a TomlElement first (which retains comments) and decode the typed config from it, so
    // the element can later serve as the comment template on save.
    private fun readConfig(path: Path): Pair<TomlTable, CharacterConfig>? =
        runCatching {
            val text = fileSystem.source(path).buffered().use { it.readString() }
            val element = toml.parseToTomlTable(text)
            element to toml.decodeFromTomlElement(CharacterConfig.serializer(), element)
        }.onFailure {
            Logger.e(it) { "Failed to read config file $path; ignoring it" }
        }.getOrNull()

    private fun persist(
        characterId: String,
        config: CharacterConfig,
    ) {
        val target = pathForCharacter(characterId)
        runCatching {
            ensureParentDir(target)
            // When we have the file's previously parsed document, re-encode against it so existing
            // comments are carried over (matched to highlights/names by their `id` so they follow an
            // entry across reorders). Brand-new files have no template and just encode fresh.
            val template = templates[characterId]
            val text =
                if (template != null) {
                    toml.encodeToString(CharacterConfig.serializer(), config, template, CONFIG_ELEMENT_KEY)
                } else {
                    toml.encodeToString(CharacterConfig.serializer(), config)
                }
            val tmp = Path(target.parent ?: rootDir, target.name + ".tmp")
            fileSystem.sink(tmp).buffered().use { it.writeString(text) }
            fileSystem.atomicMove(tmp, target)
            // Refresh the template from what we just wrote so the next save builds on current comments.
            templates[characterId] = toml.parseToTomlTable(text)
        }.onFailure {
            Logger.e(it) { "Failed to write config file $target" }
        }
    }

    private fun ensureParentDir(target: Path) {
        val parent = target.parent
        if (parent != null && fileSystem.metadataOrNull(parent) == null) {
            fileSystem.createDirectories(parent)
        }
    }

    // A sibling `.lock` file we hold the cross-process lock on while writing `target`. Kept separate
    // from the config file itself because the config is replaced via atomicMove on every save.
    private fun lockFileFor(target: Path): Path = Path(target.parent ?: rootDir, target.name + ".lock")

    // Character ids look like "gs4:tholan"; lay them out as characters/<gameCode>/<name>.toml so the
    // files are easy to browse. The authoritative id is also stored inside the file.
    private fun pathForCharacter(characterId: String): Path {
        if (characterId == GLOBAL_CHARACTER_ID) return globalFile
        val colon = characterId.indexOf(':')
        return if (colon >= 0) {
            val gameCode = sanitize(characterId.substring(0, colon))
            val name = sanitize(characterId.substring(colon + 1))
            Path(Path(charactersDir, gameCode), "$name.toml")
        } else {
            Path(charactersDir, "${sanitize(characterId)}.toml")
        }
    }

    private fun sanitize(component: String): String =
        component.map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_' || c == '.') c else '_' }.joinToString("")

    private fun characterIdFromPath(path: Path): String {
        val name = path.name.removeSuffix(".toml")
        val parentName = path.parent?.name
        // Reconstruct "gameCode:name" from the directory layout when the file omits its own id.
        return if (parentName != null && parentName != charactersDir.name) "$parentName:$name" else name
    }
}

// Identity for comment carry-over: match array entries (highlights, names) by their `id` so a
// comment follows its entry when the list is reordered. Entries without an id fall back to position.
private val CONFIG_ELEMENT_KEY: (TomlElement) -> Any? = { element ->
    (element as? TomlTable)?.get("id")?.let { (it as? TomlLiteral)?.content }
}

private fun CharacterConfig.withGeneratedIds(): Pair<CharacterConfig, Boolean> {
    var changed = false
    val highlights =
        highlights.map { highlight ->
            if (highlight.id.isNullOrBlank()) {
                changed = true
                highlight.copy(id = Uuid.random().toString())
            } else {
                highlight
            }
        }
    val names =
        names.map { name ->
            if (name.id.isNullOrBlank()) {
                changed = true
                name.copy(id = Uuid.random().toString())
            } else {
                name
            }
        }
    return copy(highlights = highlights, names = names) to changed
}
