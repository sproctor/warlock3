package warlockfe.warlock3.core.prefs.repositories

import warlockfe.warlock3.core.prefs.config.AliasConfig
import warlockfe.warlock3.core.prefs.config.AlterationConfig
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.CharacterEntry
import warlockfe.warlock3.core.prefs.config.CharacterSettingsConfig
import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.config.ConnectionConfig
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.config.HighlightConfig
import warlockfe.warlock3.core.prefs.config.HighlightStyleConfig
import warlockfe.warlock3.core.prefs.config.NameConfig
import warlockfe.warlock3.core.prefs.config.PresetStyleConfig
import warlockfe.warlock3.core.prefs.config.WindowStyleConfig
import warlockfe.warlock3.core.prefs.dao.AccountDao
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.dao.ScriptDirDao
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.prefs.export.AccountExport
import warlockfe.warlock3.core.prefs.export.AliasExport
import warlockfe.warlock3.core.prefs.export.AlterationExport
import warlockfe.warlock3.core.prefs.export.CharacterExport
import warlockfe.warlock3.core.prefs.export.ConnectionExport
import warlockfe.warlock3.core.prefs.export.HighlightExport
import warlockfe.warlock3.core.prefs.export.MacroExport
import warlockfe.warlock3.core.prefs.export.NameExport
import warlockfe.warlock3.core.prefs.export.PresetStyleExport
import warlockfe.warlock3.core.prefs.export.StyleExport
import warlockfe.warlock3.core.prefs.export.WarlockExport
import warlockfe.warlock3.core.prefs.export.WindowSettingsExport
import warlockfe.warlock3.core.prefs.models.AccountEntity
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity
import warlockfe.warlock3.core.prefs.models.ClientSettingEntity
import warlockfe.warlock3.core.prefs.models.ScriptDirEntity
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import warlockfe.warlock3.core.text.WarlockColor
import kotlin.uuid.Uuid

/** Resolution applied to a character whose settings are being imported. */
enum class ImportMode {
    /** Keep existing rows; overlay the imported ones (overwriting only on key match). */
    MERGE,

    /** Clear all of the target character's existing settings first, then insert the imported set. */
    REPLACE,
}

// Client-setting keys that now live in client.toml; everything else in the export's `settings` map
// (window size, last username, ...) stays in SQLite.
private val CLIENT_CONFIG_KEYS =
    setOf(
        "theme",
        "compassStyle",
        "scrollback",
        "markLinks",
        "showImages",
        "logPath",
        "logType",
        "logTimestamps",
        "skinFile",
        "releaseChannel",
    )

class ExportRepository(
    private val accountDao: AccountDao,
    private val characterSettingDao: CharacterSettingDao,
    private val clientSettingDao: ClientSettingDao,
    private val scriptDirDao: ScriptDirDao,
    private val windowSettingsDao: WindowSettingsDao,
    // Most settings now live in the human-editable config files rather than the database.
    private val characterConfigStore: CharacterConfigStore,
    private val clientConfigStore: ClientConfigStore,
) {
    // region Export

    suspend fun getExport(): WarlockExport {
        val registry = clientConfigStore.currentConnections()
        val characterIds =
            (
                registry.characters.map { Triple(it.id, it.gameCode, it.name) } +
                    Triple(GLOBAL_CHARACTER_ID, GLOBAL_CHARACTER_ID, GLOBAL_CHARACTER_ID)
            ).distinctBy { it.first }
        return WarlockExport(
            accounts = accountDao.getAll().map { AccountExport(username = it.username, password = null) },
            characters = characterIds.map { (id, gameCode, name) -> buildCharacterExport(id, gameCode, name) },
            connections =
                registry.connections.map { connection ->
                    ConnectionExport(
                        id = connection.id,
                        name = connection.name,
                        username = connection.username,
                        gameCode = connection.gameCode,
                        character = connection.character,
                        settings = connection.proxySettingsMap(),
                    )
                },
            settings = exportClientSettings(),
        )
    }

    suspend fun getCharacterExport(characterId: String): CharacterExport {
        val entry = clientConfigStore.currentConnections().characters.firstOrNull { it.id == characterId }
        return buildCharacterExport(
            id = characterId,
            gameCode = entry?.gameCode ?: GLOBAL_CHARACTER_ID,
            name = entry?.name ?: characterId,
        )
    }

    private suspend fun buildCharacterExport(
        id: String,
        gameCode: String,
        name: String,
    ): CharacterExport {
        val config = characterConfigStore.current(id)
        // Per-character settings: typeahead/script-prefix come from the config, the rest (geometry) from SQLite.
        val dbSettings = characterSettingDao.getByCharacter(id).associate { it.key to it.value }
        val settings =
            dbSettings +
                buildMap {
                    config.settings.typeahead?.let { put(MAX_TYPE_AHEAD_KEY, it.toString()) }
                    config.settings.scriptCommandPrefix?.let { put(SCRIPT_COMMAND_PREFIX_KEY, it) }
                }
        // Window styling lives in the config; geometry in SQLite. Union the names so neither half is lost.
        val geometryByName = windowSettingsDao.getByCharacter(id).associateBy { it.name }
        val windowNames = geometryByName.keys + config.windows.keys
        return CharacterExport(
            id = id,
            name = name,
            gameCode = gameCode,
            scriptDirectories = scriptDirDao.getByCharacter(id),
            settings = settings,
            variables = config.variables,
            aliases = config.aliases.map { AliasExport(pattern = it.pattern, replacement = it.replacement) },
            alterations =
                config.alterations.map {
                    AlterationExport(
                        pattern = it.pattern,
                        sourceStream = it.sourceStream,
                        destinationStream = it.destinationStream,
                        result = it.result,
                        ignoreCase = it.ignoreCase,
                        keepOriginal = it.keepOriginal,
                    )
                },
            highlights =
                config.highlights.map { highlight ->
                    HighlightExport(
                        pattern = highlight.pattern,
                        isRegex = highlight.isRegex,
                        matchPartialWord = highlight.matchPartialWord,
                        ignoreCase = highlight.ignoreCase,
                        sound = highlight.sound,
                        styles =
                            highlight.styles.associate { style ->
                                style.group to
                                    StyleExport(
                                        textColor = style.textColor,
                                        backgroundColor = style.backgroundColor,
                                        entireLine = style.entireLine,
                                        bold = style.bold,
                                        italic = style.italic,
                                        underline = style.underline,
                                        monospace = style.monospace,
                                    )
                            },
                    )
                },
            macros = config.macros.map { (key, value) -> MacroExport(key = key, value = value) },
            names =
                config.names.map {
                    NameExport(
                        text = it.text,
                        sound = it.sound,
                        style =
                            StyleExport(
                                textColor = it.textColor,
                                backgroundColor = it.backgroundColor,
                                bold = it.bold,
                                italic = it.italic,
                                underline = it.underline,
                                monospace = it.monospace,
                                entireLine = false,
                            ),
                    )
                },
            presets =
                config.presets.map { (presetId, preset) ->
                    PresetStyleExport(
                        id = presetId,
                        style =
                            StyleExport(
                                textColor = preset.textColor,
                                backgroundColor = preset.backgroundColor,
                                bold = preset.bold,
                                italic = preset.italic,
                                underline = preset.underline,
                                monospace = preset.monospace,
                                entireLine = preset.entireLine,
                            ),
                    )
                },
            windows =
                windowNames.map { windowName ->
                    val geometry = geometryByName[windowName]
                    val style = config.windows[windowName] ?: WindowStyleConfig()
                    WindowSettingsExport(
                        name = windowName,
                        width = geometry?.width,
                        height = geometry?.height,
                        location = geometry?.location,
                        position = geometry?.position,
                        textColor = style.textColor,
                        backgroundColor = style.backgroundColor,
                        font = style.font,
                        monoFont = style.monoFont,
                        nameFilter = style.nameFilter,
                    )
                },
        )
    }

    private fun exportClientSettings(): Map<String, String> {
        val client = clientConfigStore.currentClient()
        return buildMap {
            // Authoritative values from client.toml.
            client.theme?.let { put("theme", it) }
            client.compassStyle?.let { put("compassStyle", it) }
            client.scrollback?.let { put("scrollback", it.toString()) }
            put("markLinks", client.markLinks.toString())
            put("showImages", client.showImages.toString())
            client.logPath?.let { put("logPath", it) }
            client.logType?.let { put("logType", it) }
            put("logTimestamps", client.logTimestamps.toString())
            client.skinFile?.let { put("skinFile", it) }
            client.releaseChannel?.let { put("releaseChannel", it) }
        }
    }

    private fun ConnectionConfig.proxySettingsMap(): Map<String, String> =
        buildMap {
            put("proxyEnabled", proxyEnabled.toString())
            proxyLaunchCommand?.let { put("proxyLaunchCommand", it) }
            proxyHost?.let { put("proxyHost", it) }
            proxyPort?.let { put("proxyPort", it) }
        }

    // endregion

    // region Import

    /**
     * Restore a full backup. [resolutions] maps an exported character's id to how its data should be
     * applied; characters absent from the map are skipped. Accounts (usernames only), connections and
     * global client settings are always restored.
     */
    suspend fun importFull(
        export: WarlockExport,
        resolutions: Map<String, ImportMode>,
    ) {
        export.accounts.forEach { accountDao.save(AccountEntity(username = it.username, password = null)) }
        export.connections.forEach { connection ->
            val proxy = connection.settings
            clientConfigStore.mutateConnections { registry ->
                val updated =
                    ConnectionConfig(
                        id = connection.id,
                        name = connection.name,
                        username = connection.username,
                        gameCode = connection.gameCode,
                        character = connection.character,
                        proxyEnabled = proxy["proxyEnabled"]?.toBooleanStrictOrNull() == true,
                        proxyLaunchCommand = proxy["proxyLaunchCommand"],
                        proxyHost = proxy["proxyHost"],
                        proxyPort = proxy["proxyPort"],
                    )
                registry.copy(connections = registry.connections.filterNot { it.id == connection.id } + updated)
            }
        }
        importClientSettings(export.settings)
        export.characters.forEach { character ->
            val mode = resolutions[character.id] ?: return@forEach
            applyCharacter(targetCharacterId = character.id, data = character, mode = mode, createCharacterRow = true)
        }
    }

    /**
     * Import a single exported character's settings onto an existing [targetCharacterId]. The target
     * character's own identity (name/gameCode) is left untouched.
     */
    suspend fun importCharacter(
        source: CharacterExport,
        targetCharacterId: String,
        mode: ImportMode,
    ) {
        applyCharacter(targetCharacterId = targetCharacterId, data = source, mode = mode, createCharacterRow = false)
    }

    private suspend fun importClientSettings(settings: Map<String, String>) {
        clientConfigStore.mutateClient { current ->
            current.copy(
                theme = settings["theme"] ?: current.theme,
                compassStyle = settings["compassStyle"] ?: current.compassStyle,
                scrollback = settings["scrollback"]?.toIntOrNull() ?: current.scrollback,
                markLinks = settings["markLinks"]?.toBooleanStrictOrNull() ?: current.markLinks,
                showImages = settings["showImages"]?.toBooleanStrictOrNull() ?: current.showImages,
                logPath = settings["logPath"] ?: current.logPath,
                logType = settings["logType"] ?: current.logType,
                logTimestamps = settings["logTimestamps"]?.toBooleanStrictOrNull() ?: current.logTimestamps,
                skinFile = settings["skinFile"] ?: current.skinFile,
                releaseChannel = settings["releaseChannel"] ?: current.releaseChannel,
            )
        }
        settings.filterKeys { it !in CLIENT_CONFIG_KEYS }.forEach { (key, value) ->
            clientSettingDao.save(ClientSettingEntity(key = key, value = value))
        }
    }

    private suspend fun applyCharacter(
        targetCharacterId: String,
        data: CharacterExport,
        mode: ImportMode,
        createCharacterRow: Boolean,
    ) {
        if (createCharacterRow && targetCharacterId != GLOBAL_CHARACTER_ID) {
            val entry = CharacterEntry(id = targetCharacterId, gameCode = data.gameCode, name = data.name)
            clientConfigStore.mutateConnections { registry ->
                registry.copy(characters = registry.characters.filterNot { it.id == entry.id } + entry)
            }
        }

        if (mode == ImportMode.REPLACE) {
            scriptDirDao.deleteByCharacter(targetCharacterId)
            characterSettingDao.deleteByCharacter(targetCharacterId)
            windowSettingsDao.deleteByCharacter(targetCharacterId)
            // Config-resident sections are swapped wholesale in the mutate below.
        }

        // Build the config-resident sections from the export.
        val importedHighlights =
            data.highlights.map { highlight ->
                HighlightConfig(
                    id = Uuid.random().toString(),
                    pattern = highlight.pattern,
                    isRegex = highlight.isRegex,
                    matchPartialWord = highlight.matchPartialWord,
                    ignoreCase = highlight.ignoreCase,
                    sound = highlight.sound,
                    styles =
                        highlight.styles.map { (groupNumber, style) ->
                            HighlightStyleConfig(
                                group = groupNumber,
                                textColor = style.textColor,
                                backgroundColor = style.backgroundColor,
                                entireLine = style.entireLine,
                                bold = style.bold,
                                italic = style.italic,
                                underline = style.underline,
                                monospace = style.monospace,
                            )
                        },
                )
            }
        val importedNames =
            data.names.map { name ->
                NameConfig(
                    id = Uuid.random().toString(),
                    text = name.text,
                    sound = name.sound,
                    textColor = name.style.textColor,
                    backgroundColor = name.style.backgroundColor,
                    bold = name.style.bold,
                    italic = name.style.italic,
                    underline = name.style.underline,
                    monospace = name.style.monospace,
                )
            }
        val importedAliases =
            data.aliases.map { AliasConfig(id = Uuid.random().toString(), pattern = it.pattern, replacement = it.replacement) }
        val importedAlterations =
            data.alterations.map {
                AlterationConfig(
                    id = Uuid.random().toString(),
                    pattern = it.pattern,
                    sourceStream = it.sourceStream,
                    destinationStream = it.destinationStream,
                    result = it.result,
                    ignoreCase = it.ignoreCase,
                    keepOriginal = it.keepOriginal,
                )
            }
        val importedMacros = data.macros.associate { it.key to it.value }
        val importedPresets =
            data.presets.associate { preset ->
                preset.id to
                    PresetStyleConfig(
                        textColor = preset.style.textColor,
                        backgroundColor = preset.style.backgroundColor,
                        entireLine = preset.style.entireLine,
                        bold = preset.style.bold,
                        italic = preset.style.italic,
                        underline = preset.style.underline,
                        monospace = preset.style.monospace,
                    )
            }
        val importedWindowStyles =
            data.windows.associate { window ->
                window.name to
                    WindowStyleConfig(
                        textColor = window.textColor,
                        backgroundColor = window.backgroundColor,
                        font = window.font,
                        monoFont = window.monoFont,
                        nameFilter = window.nameFilter,
                    )
            }
        val importedCharacterSettings =
            CharacterSettingsConfig(
                typeahead = data.settings[MAX_TYPE_AHEAD_KEY]?.toIntOrNull(),
                scriptCommandPrefix = data.settings[SCRIPT_COMMAND_PREFIX_KEY],
            )

        characterConfigStore.mutate(targetCharacterId) { current ->
            if (mode == ImportMode.REPLACE) {
                current.copy(
                    highlights = importedHighlights,
                    names = importedNames,
                    variables = data.variables,
                    aliases = importedAliases,
                    alterations = importedAlterations,
                    macros = importedMacros,
                    presets = importedPresets,
                    windows = importedWindowStyles,
                    // Default fonts aren't part of the export payload; keep whatever the target had.
                    settings =
                        importedCharacterSettings.copy(
                            defaultFont = current.settings.defaultFont,
                            monoFont = current.settings.monoFont,
                        ),
                )
            } else {
                val importedPatterns = importedHighlights.mapTo(mutableSetOf()) { it.pattern }
                val importedTexts = importedNames.mapTo(mutableSetOf()) { it.text }
                val importedAliasPatterns = importedAliases.mapTo(mutableSetOf()) { it.pattern }
                val importedAlterationPatterns = importedAlterations.mapTo(mutableSetOf()) { it.pattern }
                current.copy(
                    highlights = current.highlights.filterNot { it.pattern in importedPatterns } + importedHighlights,
                    names = current.names.filterNot { it.text in importedTexts } + importedNames,
                    variables = current.variables + data.variables,
                    aliases = current.aliases.filterNot { it.pattern in importedAliasPatterns } + importedAliases,
                    alterations =
                        current.alterations.filterNot { it.pattern in importedAlterationPatterns } + importedAlterations,
                    macros = current.macros + importedMacros,
                    presets = current.presets + importedPresets,
                    windows = current.windows + importedWindowStyles,
                    settings =
                        current.settings.copy(
                            typeahead = importedCharacterSettings.typeahead ?: current.settings.typeahead,
                            scriptCommandPrefix =
                                importedCharacterSettings.scriptCommandPrefix ?: current.settings.scriptCommandPrefix,
                        ),
                )
            }
        }

        // Per-character DB settings (geometry/main-window bounds): everything not owned by the config.
        data.settings
            .filterKeys { it != MAX_TYPE_AHEAD_KEY && it != SCRIPT_COMMAND_PREFIX_KEY }
            .forEach { (key, value) ->
                characterSettingDao.save(CharacterSettingEntity(characterId = targetCharacterId, key = key, value = value))
            }

        data.scriptDirectories.forEach { path ->
            scriptDirDao.save(ScriptDirEntity(characterId = targetCharacterId, path = path))
        }

        // Window geometry → SQLite (styling was written to the config above; the entity's styling
        // columns are vestigial, so they're left at their defaults here).
        data.windows.forEach { window ->
            windowSettingsDao.save(
                WindowSettingsEntity(
                    characterId = targetCharacterId,
                    name = window.name,
                    width = window.width,
                    height = window.height,
                    location = window.location,
                    position = window.position,
                    textColor = WarlockColor.Unspecified,
                    backgroundColor = WarlockColor.Unspecified,
                    fontFamily = null,
                    fontSize = null,
                    fontWeight = null,
                    nameFilter = false,
                ),
            )
        }
    }

    // endregion
}
