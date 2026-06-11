package warlockfe.warlock3.core.prefs.config

import co.touchlab.kermit.Logger
import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlElement
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlTable
import dev.eav.tomlkt.encodeToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.KSerializer
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
                val cleared = clearSection(section, current)
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
                    element to applySection(section, element, current)
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
                    if (sectionValue(section, base) != sectionValue(section, updated)) {
                        persistSection(characterId, dir, section, updated)
                    }
                }
            }
        }
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
                    element to applySection(section, element, config)
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
            val text = encodeSection(section, config, template)
            val tmp = Path(dir, section.fileName + ".tmp")
            fileSystem.sink(tmp).buffered().use { it.writeString(text) }
            fileSystem.atomicMove(tmp, target)
            // Refresh the template from what we just wrote so the next save builds on current comments.
            templates.getOrPut(characterId) { mutableMapOf() }[section] = toml.parseToTomlTable(text)
        }.onFailure {
            Logger.e(it) { "Failed to write config file $target" }
        }
    }

    private fun <T> encodeWithTemplate(
        serializer: KSerializer<T>,
        value: T,
        template: TomlTable?,
    ): String =
        if (template != null) {
            toml.encodeToString(serializer, value, template, CONFIG_ELEMENT_KEY)
        } else {
            toml.encodeToString(serializer, value)
        }

    private fun encodeSection(
        section: Section,
        config: CharacterConfig,
        template: TomlTable?,
    ): String =
        when (section) {
            Section.HIGHLIGHTS -> encodeWithTemplate(HighlightsFile.serializer(), HighlightsFile(config.highlights), template)
            Section.NAMES -> encodeWithTemplate(NamesFile.serializer(), NamesFile(config.names), template)
            Section.ALIASES -> encodeWithTemplate(AliasesFile.serializer(), AliasesFile(config.aliases), template)
            Section.ALTERATIONS -> encodeWithTemplate(AlterationsFile.serializer(), AlterationsFile(config.alterations), template)
            Section.VARIABLES -> encodeWithTemplate(VariablesFile.serializer(), VariablesFile(config.variables), template)
            Section.MACROS -> encodeWithTemplate(MacrosFile.serializer(), MacrosFile(config.macros), template)
            Section.PRESETS -> encodeWithTemplate(PresetsFile.serializer(), PresetsFile(config.presets), template)
            Section.PROGRESS_BARS -> encodeWithTemplate(ProgressBarsFile.serializer(), ProgressBarsFile(config.progressBars), template)
            Section.WINDOWS -> encodeWithTemplate(WindowsFile.serializer(), WindowsFile(config.windows), template)
            Section.SETTINGS -> encodeWithTemplate(CharacterSettingsConfig.serializer(), config.settings, template)
        }

    private fun applySection(
        section: Section,
        element: TomlTable,
        config: CharacterConfig,
    ): CharacterConfig =
        when (section) {
            Section.HIGHLIGHTS -> {
                config.copy(highlights = toml.decodeFromTomlElement(HighlightsFile.serializer(), element).highlights)
            }

            Section.NAMES -> {
                config.copy(names = toml.decodeFromTomlElement(NamesFile.serializer(), element).names)
            }

            Section.ALIASES -> {
                config.copy(aliases = toml.decodeFromTomlElement(AliasesFile.serializer(), element).aliases)
            }

            Section.ALTERATIONS -> {
                config.copy(alterations = toml.decodeFromTomlElement(AlterationsFile.serializer(), element).alterations)
            }

            Section.VARIABLES -> {
                config.copy(variables = toml.decodeFromTomlElement(VariablesFile.serializer(), element).variables)
            }

            Section.MACROS -> {
                config.copy(macros = toml.decodeFromTomlElement(MacrosFile.serializer(), element).macros)
            }

            Section.PRESETS -> {
                config.copy(presets = toml.decodeFromTomlElement(PresetsFile.serializer(), element).presets)
            }

            Section.PROGRESS_BARS -> {
                config.copy(progressBars = toml.decodeFromTomlElement(ProgressBarsFile.serializer(), element).progressBars)
            }

            Section.WINDOWS -> {
                config.copy(windows = toml.decodeFromTomlElement(WindowsFile.serializer(), element).windows)
            }

            Section.SETTINGS -> {
                config.copy(settings = toml.decodeFromTomlElement(CharacterSettingsConfig.serializer(), element))
            }
        }

    private fun clearSection(
        section: Section,
        config: CharacterConfig,
    ): CharacterConfig =
        when (section) {
            Section.HIGHLIGHTS -> config.copy(highlights = emptyList())
            Section.NAMES -> config.copy(names = emptyList())
            Section.ALIASES -> config.copy(aliases = emptyList())
            Section.ALTERATIONS -> config.copy(alterations = emptyList())
            Section.VARIABLES -> config.copy(variables = emptyMap())
            Section.MACROS -> config.copy(macros = emptyMap())
            Section.PRESETS -> config.copy(presets = emptyMap())
            Section.PROGRESS_BARS -> config.copy(progressBars = emptyMap())
            Section.WINDOWS -> config.copy(windows = emptyMap())
            Section.SETTINGS -> config.copy(settings = CharacterSettingsConfig())
        }

    private fun sectionValue(
        section: Section,
        config: CharacterConfig,
    ): Any =
        when (section) {
            Section.HIGHLIGHTS -> config.highlights
            Section.NAMES -> config.names
            Section.ALIASES -> config.aliases
            Section.ALTERATIONS -> config.alterations
            Section.VARIABLES -> config.variables
            Section.MACROS -> config.macros
            Section.PRESETS -> config.presets
            Section.PROGRESS_BARS -> config.progressBars
            Section.WINDOWS -> config.windows
            Section.SETTINGS -> config.settings
        }

    private fun isSectionEmpty(
        section: Section,
        config: CharacterConfig,
    ): Boolean =
        when (val value = sectionValue(section, config)) {
            is List<*> -> value.isEmpty()
            is Map<*, *> -> value.isEmpty()
            is CharacterSettingsConfig -> value == CharacterSettingsConfig()
            else -> true
        }
}

// The per-section file contents. The file name already names the section, but each is wrapped in a
// single-key table so the TOML root is always a table (settings.toml uses CharacterSettingsConfig
// directly, since its fields are already the root scalars).
private enum class Section(
    val fileName: String,
) {
    HIGHLIGHTS("highlights.toml"),
    NAMES("names.toml"),
    ALIASES("aliases.toml"),
    ALTERATIONS("alterations.toml"),
    VARIABLES("variables.toml"),
    MACROS("macros.toml"),
    PRESETS("presets.toml"),
    PROGRESS_BARS("progressbars.toml"),
    WINDOWS("windows.toml"),
    SETTINGS("settings.toml"),
}

// Sections whose entries carry an `id` that may be generated on load.
private val ID_SECTIONS = listOf(Section.HIGHLIGHTS, Section.NAMES, Section.ALIASES, Section.ALTERATIONS)

// Identity for comment carry-over: match array entries (highlights, names, ...) by their `id` so a
// comment follows its entry when the list is reordered. Entries without an id fall back to position.
internal val CONFIG_ELEMENT_KEY: (TomlElement) -> Any? = { element ->
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
    val aliases =
        aliases.map { alias ->
            if (alias.id.isNullOrBlank()) {
                changed = true
                alias.copy(id = Uuid.random().toString())
            } else {
                alias
            }
        }
    val alterations =
        alterations.map { alteration ->
            if (alteration.id.isNullOrBlank()) {
                changed = true
                alteration.copy(id = Uuid.random().toString())
            } else {
                alteration
            }
        }
    return copy(highlights = highlights, names = names, aliases = aliases, alterations = alterations) to changed
}
