package warlockfe.warlock3.core.prefs.config

import co.touchlab.kermit.Logger
import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlElement
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readString
import kotlin.uuid.Uuid

const val GLOBAL_CHARACTER_ID = "global"

/**
 * Human-editable, file-backed store for per-character config (highlights, names, variables, macros,
 * aliases, alterations, presets, progress bars, window styling, settings).
 *
 * Each character gets a directory `characters/<gameCode>/<name>/` (or `characters/global/` for the
 * shared config), with one TOML file per section (`highlights.toml`, `variables.toml`, ...). Splitting
 * by section keeps high-churn sections (e.g. variables, rewritten constantly by scripts) cheap to save
 * and lets a hand edit to one section not touch the others. A [mutate] only rewrites the section files
 * that actually changed.
 *
 * The store keeps every config in memory as the single source of truth (so reads are reactive and
 * cheap). The Highlight/Name/Variable/... repositories all funnel through this store so their writes
 * never clobber each other.
 */
class CharacterConfigStore(
    configDirectory: String,
    private val fileSystem: FileSystem,
) {
    private val rootDir = Path(configDirectory)
    private val charactersDir = Path(rootDir, "characters")

    private val toml =
        Toml {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val writeMutex = Mutex()
    private val state = MutableStateFlow<Map<String, CharacterConfig>>(emptyMap())

    // The last-parsed TOML document for each (character, section), used as the comment/formatting
    // template when we re-encode on save so hand-written comments survive the rewrite. Only touched
    // during [load] and on the write path (under [writeMutex]).
    private val templates = mutableMapOf<String, MutableMap<Section, TomlTable>>()

    /** Exposed for tests/diagnostics; observers should prefer [observe]. */
    val configs: StateFlow<Map<String, CharacterConfig>> = state

    fun observe(characterId: String): Flow<CharacterConfig> =
        state
            .map { it[characterId] ?: CharacterConfig(character = characterId) }
            .distinctUntilChanged()

    fun current(characterId: String): CharacterConfig = state.value[characterId] ?: CharacterConfig(character = characterId)

    fun snapshot(): Map<String, CharacterConfig> = state.value

    /**
     * The on-disk directory where a character's files live (the per-section config files, and the
     * command-history file). Exposed so sibling repositories can store their files alongside the
     * config using the same `characters/<gameCode>/<name>/` layout.
     */
    fun directoryFor(characterId: String): Path = dirForCharacter(characterId)

    /**
     * Reads every character's config files from disk into memory. Hand-authored entries missing an
     * `id` are assigned one and the affected section files are rewritten so the id stays stable across
     * launches. Safe to call before any data exists (results in an empty store).
     */
    suspend fun load() {
        templates.clear()
        val loaded = mutableMapOf<String, CharacterConfig>()
        for (dir in listCharacterDirs()) {
            val characterId = characterIdFromDir(dir)
            val (config, sectionTemplates) = readCharacterDir(dir, characterId)
            loaded[characterId] = config
            templates[characterId] = sectionTemplates.toMutableMap()
        }
        // Fill in any missing ids, persisting only the sections we actually changed.
        val normalized = mutableMapOf<String, CharacterConfig>()
        val changed = mutableSetOf<String>()
        for ((key, config) in loaded) {
            val (fixed, didChange) = config.withGeneratedIds()
            normalized[key] = fixed
            if (didChange) changed += key
        }
        state.value = normalized
        for (key in changed) {
            writeMutex.withLock {
                ensureDir(charactersDir)
                withFileLock(lockFileFor(key)) {
                    val dir = dirForCharacter(key)
                    // Only id-bearing sections can change during normalization.
                    for (section in ID_SECTIONS) persistSection(key, dir, section, normalized.getValue(key))
                }
            }
        }
    }

    /**
     * Watches the config directory and reloads any section file changed out of band (a hand edit, or a
     * save from another app instance) into memory, so observers see the latest content. Call once after
     * [load]; the watch runs for the lifetime of [scope].
     */
    fun startWatching(scope: CoroutineScope) {
        scope.launch {
            watchConfigChanges(rootDir.toString()).collect { changedPath ->
                reloadFile(Path(changedPath))
            }
        }
    }

    // Reload a single externally-changed section file. Reads under [writeMutex] so it can't race a
    // concurrent mutate, and skips when the content already matches memory (e.g. our own save echoing
    // back). Files that aren't a known section (client.toml, etc.) are ignored.
    private suspend fun reloadFile(path: Path) {
        val section = Section.entries.firstOrNull { it.fileName == path.name } ?: return
        val dir = path.parent ?: return
        val characterId = characterIdFromDir(dir)
        writeMutex.withLock {
            val current = state.value[characterId] ?: CharacterConfig(character = characterId)
            if (fileSystem.metadataOrNull(path) == null) {
                // Section file deleted out of band: clear that section.
                val cleared = section.clear(current)
                if (cleared != current) {
                    templates[characterId]?.remove(section)
                    state.value = state.value + (characterId to cleared)
                }
                return@withLock
            }
            val read =
                runCatching {
                    val text = fileSystem.source(path).buffered().use { it.readString() }
                    val element = toml.parseToTomlTable(text)
                    element to section.decodeInto(toml, element, current)
                }.onFailure {
                    Logger.e(it) { "Failed to reload config file $path; ignoring it" }
                }.getOrNull() ?: return@withLock
            val (element, updated) = read
            if (updated != current) {
                templates.getOrPut(characterId) { mutableMapOf() }[section] = element
                state.value = state.value + (characterId to updated)
            }
        }
    }

    suspend fun mutate(
        characterId: String,
        transform: (CharacterConfig) -> CharacterConfig,
    ) {
        val dir = dirForCharacter(characterId)
        writeMutex.withLock {
            ensureDir(charactersDir)
            withFileLock(lockFileFor(characterId)) {
                // Read-modify-write against the current on-disk files so a concurrent write from another
                // app instance isn't clobbered: re-read inside the lock, apply the transform to that,
                // then persist only the sections that changed.
                val (onDisk, sectionTemplates) = readCharacterDir(dir, characterId)
                templates[characterId] = sectionTemplates.toMutableMap()
                val base = onDisk.copy(character = characterId)
                val updated = transform(base).copy(character = characterId)
                state.value = state.value + (characterId to updated)
                for (section in Section.entries) {
                    if (section.get(base) != section.get(updated)) {
                        persistSection(characterId, dir, section, updated)
                    }
                }
            }
        }
    }

    /**
     * Overwrite a single section file at [path] with [content] (raw TOML), serialized against in-app
     * edits via the same in-memory write lock and cross-process file lock that [mutate] uses, then
     * update the in-memory config to match. The bytes are written **verbatim** (not re-encoded), so a
     * caller that tracks content hashes (e.g. settings sync) sees exactly what it wrote.
     *
     * Intended for writing a file pulled from an external source (settings sync) without racing a
     * concurrent in-app save. [path] must be a known section file under `characters/`; returns false
     * (writing nothing) for anything else, so the caller can fall back to its own write.
     */
    suspend fun writeSectionFile(
        path: Path,
        content: String,
    ): Boolean {
        val section = Section.entries.firstOrNull { it.fileName == path.name } ?: return false
        val dir = path.parent ?: return false
        val characterId = characterIdFromDir(dir)
        writeMutex.withLock {
            ensureDir(dir)
            withFileLock(lockFileFor(characterId)) {
                runCatching {
                    fileSystem.writeTextAtomically(path, content)
                    // Update in-memory state + comment template from exactly the bytes we wrote.
                    val element = toml.parseToTomlTable(content)
                    val current = state.value[characterId] ?: CharacterConfig(character = characterId)
                    val updated = section.decodeInto(toml, element, current).copy(character = characterId)
                    templates.getOrPut(characterId) { mutableMapOf() }[section] = element
                    state.value = state.value + (characterId to updated)
                }.onFailure {
                    Logger.e(it) { "Failed to write section file $path" }
                }
            }
        }
        return true
    }

    // --- file layout ---

    private fun listCharacterDirs(): List<Path> {
        if (fileSystem.metadataOrNull(charactersDir)?.isDirectory != true) return emptyList()
        val dirs = mutableListOf<Path>()
        for (entry in fileSystem.list(charactersDir)) {
            if (fileSystem.metadataOrNull(entry)?.isDirectory != true) continue
            if (isCharacterDir(entry)) {
                // e.g. characters/global
                dirs += entry
            } else {
                // a gameCode dir: its subdirectories are the character dirs
                for (sub in fileSystem.list(entry)) {
                    if (fileSystem.metadataOrNull(sub)?.isDirectory == true && isCharacterDir(sub)) {
                        dirs += sub
                    }
                }
            }
        }
        return dirs
    }

    // A character dir is one that directly contains at least one section file.
    private fun isCharacterDir(dir: Path): Boolean = Section.entries.any { fileSystem.metadataOrNull(Path(dir, it.fileName)) != null }

    // Character ids look like "gs4:tholan"; lay them out as characters/<gameCode>/<name>/ so the files
    // are easy to browse. The id isn't stored in the files; it's derived from the directory layout.
    private fun dirForCharacter(characterId: String): Path {
        if (characterId == GLOBAL_CHARACTER_ID) return Path(charactersDir, GLOBAL_CHARACTER_ID)
        val colon = characterId.indexOf(':')
        return if (colon >= 0) {
            val gameCode = sanitize(characterId.substring(0, colon))
            val name = sanitize(characterId.substring(colon + 1))
            Path(Path(charactersDir, gameCode), name)
        } else {
            Path(charactersDir, sanitize(characterId))
        }
    }

    private fun characterIdFromDir(dir: Path): String {
        val name = dir.name
        val parent = dir.parent
        // characters/<gameCode>/<name> -> "gameCode:name"; characters/<name> (e.g. global) -> "name".
        return if (parent != null && parent.name != charactersDir.name) "${parent.name}:$name" else name
    }

    private fun sanitize(component: String): String =
        component.map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_' || c == '.') c else '_' }.joinToString("")

    // A hidden `.lock` file in characters/ that we hold the cross-process lock on while writing a
    // character's section files. Kept out of the character dir so it isn't a config file and doesn't
    // force the dir to exist before locking.
    private fun lockFileFor(characterId: String): Path = Path(charactersDir, ".${sanitize(characterId)}.lock")

    private fun ensureDir(dir: Path) {
        if (fileSystem.metadataOrNull(dir) == null) {
            fileSystem.createDirectories(dir)
        }
    }

    // --- per-section read/write ---

    private fun readCharacterDir(
        dir: Path,
        characterId: String,
    ): Pair<CharacterConfig, Map<Section, TomlTable>> {
        var config = CharacterConfig(character = characterId)
        val sectionTemplates = mutableMapOf<Section, TomlTable>()
        for (section in Section.entries) {
            val path = Path(dir, section.fileName)
            if (fileSystem.metadataOrNull(path) == null) continue
            val read =
                runCatching {
                    val text = fileSystem.source(path).buffered().use { it.readString() }
                    val element = toml.parseToTomlTable(text)
                    element to section.decodeInto(toml, element, config)
                }.onFailure {
                    Logger.e(it) { "Failed to read config file $path; ignoring it" }
                }.getOrNull() ?: continue
            sectionTemplates[section] = read.first
            config = read.second
        }
        return config to sectionTemplates
    }

    private fun persistSection(
        characterId: String,
        dir: Path,
        section: Section,
        config: CharacterConfig,
    ) {
        val target = Path(dir, section.fileName)
        if (isSectionEmpty(section, config)) {
            // Nothing to store: drop the file (and its template) rather than leave an empty one behind.
            runCatching {
                if (fileSystem.metadataOrNull(target) != null) fileSystem.delete(target)
            }.onFailure { Logger.e(it) { "Failed to delete config file $target" } }
            templates[characterId]?.remove(section)
            return
        }
        runCatching {
            ensureDir(dir)
            // Re-encode against the previously parsed document when we have one so existing comments are
            // carried over (matched to list entries by their `id` so a comment follows an entry across
            // reorders). Brand-new files have no template and just encode fresh.
            val template = templates[characterId]?.get(section)
            val text = section.encode(toml, config, template)
            fileSystem.writeTextAtomically(target, text)
            // Refresh the template from what we just wrote so the next save builds on current comments.
            templates.getOrPut(characterId) { mutableMapOf() }[section] = toml.parseToTomlTable(text)
        }.onFailure {
            Logger.e(it) { "Failed to write config file $target" }
        }
    }

    private fun isSectionEmpty(
        section: Section,
        config: CharacterConfig,
    ): Boolean =
        when (val value = section.get(config)) {
            is List<*> -> value.isEmpty()
            is Map<*, *> -> value.isEmpty()
            is ActionsFile -> value.toolbar.isEmpty() && value.actions.isEmpty()
            is CharacterSettingsConfig -> value == CharacterSettingsConfig()
            else -> true
        }
}

// The per-section file contents. The file name already names the section, but each is wrapped in a
// single-key table so the TOML root is always a table (settings.toml uses CharacterSettingsConfig
// directly, since its fields are already the root scalars). Each section carries its own
// read/encode/clear behavior so adding one is a single edit here rather than several parallel `when`s.
private enum class Section(
    val fileName: String,
    val get: (config: CharacterConfig) -> Any,
    val clear: (config: CharacterConfig) -> CharacterConfig,
    val encode: (toml: Toml, config: CharacterConfig, template: TomlTable?) -> String,
    val decodeInto: (toml: Toml, element: TomlTable, config: CharacterConfig) -> CharacterConfig,
) {
    HIGHLIGHTS(
        fileName = "highlights.toml",
        get = { it.highlights },
        clear = { it.copy(highlights = emptyList()) },
        encode = { toml, config, template ->
            toml.encodeWithTemplate(HighlightsFile.serializer(), HighlightsFile(config.highlights), template)
        },
        decodeInto = { toml, element, config ->
            config.copy(highlights = toml.decodeFromTomlElement(HighlightsFile.serializer(), element).highlights)
        },
    ),
    NAMES(
        fileName = "names.toml",
        get = { it.names },
        clear = { it.copy(names = emptyList()) },
        encode = { toml, config, template ->
            toml.encodeWithTemplate(NamesFile.serializer(), NamesFile(config.names), template)
        },
        decodeInto = { toml, element, config ->
            config.copy(names = toml.decodeFromTomlElement(NamesFile.serializer(), element).names)
        },
    ),
    ALIASES(
        fileName = "aliases.toml",
        get = { it.aliases },
        clear = { it.copy(aliases = emptyList()) },
        encode = { toml, config, template ->
            toml.encodeWithTemplate(AliasesFile.serializer(), AliasesFile(config.aliases), template)
        },
        decodeInto = { toml, element, config ->
            config.copy(aliases = toml.decodeFromTomlElement(AliasesFile.serializer(), element).aliases)
        },
    ),
    ALTERATIONS(
        fileName = "alterations.toml",
        get = { it.alterations },
        clear = { it.copy(alterations = emptyList()) },
        encode = { toml, config, template ->
            toml.encodeWithTemplate(AlterationsFile.serializer(), AlterationsFile(config.alterations), template)
        },
        decodeInto = { toml, element, config ->
            config.copy(alterations = toml.decodeFromTomlElement(AlterationsFile.serializer(), element).alterations)
        },
    ),
    ACTIONS(
        fileName = "actions.toml",
        // The actions pool and the toolbar share one file, so the change-detection key covers both.
        get = { ActionsFile(it.toolbar, it.actions) },
        clear = { it.copy(toolbar = emptyList(), actions = emptyList()) },
        encode = { toml, config, template ->
            toml.encodeWithTemplate(ActionsFile.serializer(), ActionsFile(config.toolbar, config.actions), template)
        },
        decodeInto = { toml, element, config ->
            val file = toml.decodeFromTomlElement(ActionsFile.serializer(), element)
            config.copy(toolbar = file.toolbar, actions = file.actions)
        },
    ),
    VARIABLES(
        fileName = "variables.toml",
        get = { it.variables },
        clear = { it.copy(variables = emptyMap()) },
        encode = { toml, config, template ->
            toml.encodeWithTemplate(VariablesFile.serializer(), VariablesFile(config.variables), template)
        },
        decodeInto = { toml, element, config ->
            config.copy(variables = toml.decodeFromTomlElement(VariablesFile.serializer(), element).variables)
        },
    ),
    MACROS(
        fileName = "macros.toml",
        get = { it.macros },
        clear = { it.copy(macros = emptyMap()) },
        encode = { toml, config, template ->
            toml.encodeWithTemplate(MacrosFile.serializer(), MacrosFile(config.macros), template)
        },
        decodeInto = { toml, element, config ->
            config.copy(macros = toml.decodeFromTomlElement(MacrosFile.serializer(), element).macros)
        },
    ),
    PRESETS(
        fileName = "presets.toml",
        get = { it.presets },
        clear = { it.copy(presets = emptyMap()) },
        encode = { toml, config, template ->
            toml.encodeWithTemplate(PresetsFile.serializer(), PresetsFile(config.presets), template)
        },
        decodeInto = { toml, element, config ->
            config.copy(presets = toml.decodeFromTomlElement(PresetsFile.serializer(), element).presets)
        },
    ),
    PROGRESS_BARS(
        fileName = "progressbars.toml",
        get = { it.progressBars },
        clear = { it.copy(progressBars = emptyMap()) },
        encode = { toml, config, template ->
            toml.encodeWithTemplate(ProgressBarsFile.serializer(), ProgressBarsFile(config.progressBars), template)
        },
        decodeInto = { toml, element, config ->
            config.copy(progressBars = toml.decodeFromTomlElement(ProgressBarsFile.serializer(), element).progressBars)
        },
    ),
    WINDOWS(
        fileName = "windows.toml",
        get = { it.windows },
        clear = { it.copy(windows = emptyMap()) },
        encode = { toml, config, template ->
            toml.encodeWithTemplate(WindowsFile.serializer(), WindowsFile(config.windows), template)
        },
        decodeInto = { toml, element, config ->
            config.copy(windows = toml.decodeFromTomlElement(WindowsFile.serializer(), element).windows)
        },
    ),
    SETTINGS(
        fileName = "settings.toml",
        get = { it.settings },
        clear = { it.copy(settings = CharacterSettingsConfig()) },
        encode = { toml, config, template ->
            toml.encodeWithTemplate(CharacterSettingsConfig.serializer(), config.settings, template)
        },
        decodeInto = { toml, element, config ->
            config.copy(settings = toml.decodeFromTomlElement(CharacterSettingsConfig.serializer(), element))
        },
    ),
}

// Sections whose entries carry an `id` that may be generated on load.
private val ID_SECTIONS = listOf(Section.HIGHLIGHTS, Section.NAMES, Section.ALIASES, Section.ALTERATIONS, Section.ACTIONS)

// Identity for comment carry-over: match array entries (highlights, names, ...) by their `id` so a
// comment follows its entry when the list is reordered. Entries without an id fall back to position.
internal val CONFIG_ELEMENT_KEY: (TomlElement) -> Any? = { element ->
    (element as? TomlTable)?.get("id")?.let { (it as? TomlLiteral)?.content }
}

private fun CharacterConfig.withGeneratedIds(): Pair<CharacterConfig, Boolean> {
    var changed = false

    fun <T> List<T>.fillMissingIds(
        getId: (T) -> String?,
        withId: (T, String) -> T,
    ): List<T> =
        map { item ->
            if (getId(item).isNullOrBlank()) {
                changed = true
                withId(item, Uuid.random().toString())
            } else {
                item
            }
        }
    return copy(
        highlights = highlights.fillMissingIds({ it.id }, { item, id -> item.copy(id = id) }),
        names = names.fillMissingIds({ it.id }, { item, id -> item.copy(id = id) }),
        aliases = aliases.fillMissingIds({ it.id }, { item, id -> item.copy(id = id) }),
        alterations = alterations.fillMissingIds({ it.id }, { item, id -> item.copy(id = id) }),
        actions = actions.fillMissingIds({ it.id }, { item, id -> item.copy(id = id) }),
    ) to changed
}
