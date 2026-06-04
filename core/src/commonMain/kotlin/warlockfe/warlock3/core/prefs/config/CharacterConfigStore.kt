package warlockfe.warlock3.core.prefs.config

import co.touchlab.kermit.Logger
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
import net.peanuuutz.tomlkt.Toml
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

    /** Exposed for tests/diagnostics; observers should prefer [observe]. */
    val configs: StateFlow<Map<String, CharacterConfig>> = state

    fun observe(characterId: String): Flow<CharacterConfig> =
        state
            .map { it[characterId] ?: CharacterConfig(character = characterId) }
            .distinctUntilChanged()

    fun current(characterId: String): CharacterConfig =
        state.value[characterId] ?: CharacterConfig(character = characterId)

    fun snapshot(): Map<String, CharacterConfig> = state.value

    /**
     * Reads every config file from disk into memory. Hand-authored entries missing an `id` are
     * assigned one and the file is rewritten so the id stays stable across launches. Safe to call
     * before any data exists (results in an empty store).
     */
    suspend fun load() {
        val loaded = mutableMapOf<String, CharacterConfig>()
        for (path in listConfigFiles()) {
            val config = readConfig(path) ?: continue
            val key = config.character.ifEmpty { characterIdFromFileName(path) }
            loaded[key] = config.copy(character = key)
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
            writeMutex.withLock { persist(key, normalized.getValue(key)) }
        }
    }

    suspend fun mutate(
        characterId: String,
        transform: (CharacterConfig) -> CharacterConfig,
    ) {
        writeMutex.withLock {
            val current = state.value[characterId] ?: CharacterConfig(character = characterId)
            val updated = transform(current).copy(character = characterId)
            state.value = state.value + (characterId to updated)
            persist(characterId, updated)
        }
    }

    private fun listConfigFiles(): List<Path> {
        val files = mutableListOf<Path>()
        if (fileSystem.metadataOrNull(globalFile) != null) {
            files += globalFile
        }
        val charactersMeta = fileSystem.metadataOrNull(charactersDir)
        if (charactersMeta?.isDirectory == true) {
            files += fileSystem.list(charactersDir).filter { it.name.endsWith(".toml") }
        }
        return files
    }

    private fun readConfig(path: Path): CharacterConfig? =
        runCatching {
            val text = fileSystem.source(path).buffered().use { it.readString() }
            toml.decodeFromString(CharacterConfig.serializer(), text)
        }.onFailure {
            Logger.e(it) { "Failed to read config file $path; ignoring it" }
        }.getOrNull()

    private fun persist(characterId: String, config: CharacterConfig) {
        val target =
            if (characterId == GLOBAL_CHARACTER_ID) {
                globalFile
            } else {
                Path(charactersDir, fileNameForCharacter(characterId))
            }
        runCatching {
            val parent = target.parent
            if (parent != null && fileSystem.metadataOrNull(parent) == null) {
                fileSystem.createDirectories(parent)
            }
            val text = toml.encodeToString(CharacterConfig.serializer(), config)
            val tmp = Path(target.parent ?: rootDir, target.name + ".tmp")
            fileSystem.sink(tmp).buffered().use { it.writeString(text) }
            fileSystem.atomicMove(tmp, target)
        }.onFailure {
            Logger.e(it) { "Failed to write config file $target" }
        }
    }

    private fun fileNameForCharacter(characterId: String): String {
        // Character ids look like "gs4:tholan"; sanitize anything a filesystem might dislike.
        // The authoritative id is stored inside the file, so this only needs to be stable.
        val safe = characterId.map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_' || c == '.') c else '_' }.joinToString("")
        return "$safe.toml"
    }

    private fun characterIdFromFileName(path: Path): String =
        path.name.removeSuffix(".toml")
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
