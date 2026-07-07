package warlockfe.warlock3.core.prefs.config

import co.touchlab.kermit.Logger
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import warlockfe.warlock3.core.prefs.dao.AliasDao
import warlockfe.warlock3.core.prefs.dao.AlterationDao
import warlockfe.warlock3.core.prefs.dao.CharacterDao
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.dao.ConnectionDao
import warlockfe.warlock3.core.prefs.dao.HighlightDao
import warlockfe.warlock3.core.prefs.dao.NameDao
import warlockfe.warlock3.core.prefs.dao.PresetStyleDao
import warlockfe.warlock3.core.prefs.dao.ProgressBarSettingDao
import warlockfe.warlock3.core.prefs.dao.VariableDao
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.prefs.mappers.toHighlight
import warlockfe.warlock3.core.prefs.models.ConnectionWithSettings
import warlockfe.warlock3.core.prefs.repositories.MAX_TYPE_AHEAD_KEY
import warlockfe.warlock3.core.prefs.repositories.MacroRepository
import warlockfe.warlock3.core.prefs.repositories.SCRIPT_COMMAND_PREFIX_KEY
import warlockfe.warlock3.core.text.FontConfig

/**
 * One-time migration of settings out of the SQLite database and into the human-editable TOML config
 * files: highlights, names, variables, macros, aliases, alterations, presets, progress bars, window
 * styling, per-character settings, client settings, and the connection/character registry. Reads
 * existing rows through the legacy DAOs and writes them through the config stores. The database
 * tables are left intact so this is non-destructive and reversible.
 *
 * Runs once, guarded by the presence of `client.toml`: the migration always writes that file when it
 * finishes (even with nothing to migrate), so its existence means the migration has already run.
 */
class ConfigMigration(
    private val store: CharacterConfigStore,
    private val clientConfigStore: ClientConfigStore,
    private val characterDao: CharacterDao,
    private val highlightDao: HighlightDao,
    private val nameDao: NameDao,
    private val variableDao: VariableDao,
    private val aliasDao: AliasDao,
    private val alterationDao: AlterationDao,
    private val presetStyleDao: PresetStyleDao,
    private val progressBarSettingDao: ProgressBarSettingDao,
    private val windowSettingsDao: WindowSettingsDao,
    private val characterSettingDao: CharacterSettingDao,
    private val clientSettingDao: ClientSettingDao,
    private val connectionDao: ConnectionDao,
    private val macroRepository: MacroRepository,
    private val fileSystem: FileSystem,
    configDirectory: String,
) {
    private val rootDir = Path(configDirectory)
    private val clientConfigFile = Path(rootDir, "client.toml")

    suspend fun migrateIfNeeded() {
        if (fileSystem.metadataOrNull(clientConfigFile) != null) return
        runCatching {
            for (id in characterIds()) {
                migrateCharacter(id)
            }
            migrateClientSettings()
            migrateConnectionsAndCharacters()
        }.onFailure {
            Logger.e(it) { "Failed to migrate settings to config files" }
        }
    }

    private suspend fun characterIds(): List<String> =
        buildList {
            add(GLOBAL_CHARACTER_ID)
            runCatching { characterDao.getAll() }
                .getOrDefault(emptyList())
                .forEach { add(it.id) }
        }.distinct()

    private suspend fun migrateCharacter(id: String) {
        val highlights = highlightDao.getHighlightsByCharacter(id).map { it.toHighlight().toConfig() }
        val names = nameDao.getByCharacter(id).map { it.toConfig() }
        val variables = variableDao.getAllByCharacter(id).associate { it.name to it.value }
        val aliases = aliasDao.getByCharacter(id).map { it.toConfig() }
        val alterations = alterationDao.getAlterationsByCharacter(id).map { it.toConfig() }
        val macros = macroRepository.exportLegacyMacros(id)
        val presets =
            presetStyleDao.getByCharacter(id).associate { entity ->
                entity.presetId to
                    PresetStyleConfig(
                        textColor = entity.textColor,
                        backgroundColor = entity.backgroundColor,
                        entireLine = entity.entireLine,
                        bold = entity.bold,
                        italic = entity.italic,
                        underline = entity.underline,
                    )
            }
        val progressBars = progressBarSettingDao.getByCharacter(id).associate { it.id to it.toConfig() }
        val windows =
            windowSettingsDao
                .getByCharacter(id)
                .mapNotNull { entity ->
                    val style =
                        WindowStyleConfig(
                            textColor = entity.textColor,
                            backgroundColor = entity.backgroundColor,
                            font =
                                FontConfig(
                                    family = entity.fontFamily,
                                    size = entity.fontSize,
                                    weight = entity.fontWeight,
                                ).takeUnless { it.isEmpty() },
                            nameFilter = entity.nameFilter,
                        )
                    // Only carry over windows that actually customized their styling.
                    if (style == WindowStyleConfig()) null else entity.name to style
                }.toMap()
        val characterSettings = characterSettingDao.getByCharacter(id).associate { it.key to it.value }
        val settings =
            CharacterSettingsConfig(
                typeahead = characterSettings[MAX_TYPE_AHEAD_KEY]?.toIntOrNull(),
                scriptCommandPrefix = characterSettings[SCRIPT_COMMAND_PREFIX_KEY],
            )

        val hasSettings = settings != CharacterSettingsConfig()
        if (highlights.isEmpty() && names.isEmpty() && variables.isEmpty() && aliases.isEmpty() &&
            alterations.isEmpty() && macros.isEmpty() && presets.isEmpty() && progressBars.isEmpty() &&
            windows.isEmpty() && !hasSettings
        ) {
            return
        }

        store.mutate(id) { current ->
            current.copy(
                highlights = highlights.ifEmpty { current.highlights },
                names = names.ifEmpty { current.names },
                variables = variables.ifEmpty { current.variables },
                aliases = aliases.ifEmpty { current.aliases },
                alterations = alterations.ifEmpty { current.alterations },
                macros = macros.ifEmpty { current.macros },
                presets = presets.ifEmpty { current.presets },
                progressBars = progressBars.ifEmpty { current.progressBars },
                windows = windows.ifEmpty { current.windows },
                settings = if (hasSettings) settings else current.settings,
            )
        }
    }

    private suspend fun migrateClientSettings() {
        val map = clientSettingDao.getAll().associate { it.key to it.value }
        // Always write (even when there's nothing to migrate) so client.toml exists afterward and
        // acts as the "already migrated" guard on the next launch.
        clientConfigStore.mutateClient { current ->
            current.copy(
                theme = map["theme"] ?: current.theme,
                scrollback = map["scrollback"]?.toIntOrNull() ?: current.scrollback,
                markLinks = map["markLinks"]?.toBooleanStrictOrNull() ?: current.markLinks,
                showImages = map["showImages"]?.toBooleanStrictOrNull() ?: current.showImages,
                logPath = map["logPath"] ?: current.logPath,
                logType = map["logType"] ?: current.logType,
                logTimestamps = map["logTimestamps"]?.toBooleanStrictOrNull() ?: current.logTimestamps,
                skinFile = map["skinFile"] ?: current.skinFile,
                releaseChannel = map["releaseChannel"] ?: current.releaseChannel,
            )
        }
    }

    private suspend fun migrateConnectionsAndCharacters() {
        val connections = connectionDao.getAllWithSettings().map { it.toConnectionConfig() }
        val characters = characterDao.getAll().map { CharacterEntry(id = it.id, gameCode = it.gameCode, name = it.name) }
        if (connections.isEmpty() && characters.isEmpty()) return
        clientConfigStore.mutateConnections { current ->
            current.copy(
                characters = characters.ifEmpty { current.characters },
                connections = connections.ifEmpty { current.connections },
            )
        }
    }
}

private fun ConnectionWithSettings.toConnectionConfig(): ConnectionConfig {
    val s = settings.associate { it.key to it.value }
    return ConnectionConfig(
        id = connection.id,
        name = connection.name,
        username = connection.username,
        gameCode = connection.gameCode,
        character = connection.character,
        proxyEnabled = s["proxyEnabled"]?.toBooleanStrictOrNull() == true,
        proxyLaunchCommand = s["proxyLaunchCommand"],
        proxyHost = s["proxyHost"],
        proxyPort = s["proxyPort"],
    )
}
