package warlockfe.warlock3.core.prefs.repositories

import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.HighlightConfig
import warlockfe.warlock3.core.prefs.config.HighlightStyleConfig
import warlockfe.warlock3.core.prefs.config.NameConfig
import warlockfe.warlock3.core.prefs.dao.AccountDao
import warlockfe.warlock3.core.prefs.dao.AliasDao
import warlockfe.warlock3.core.prefs.dao.AlterationDao
import warlockfe.warlock3.core.prefs.dao.CharacterDao
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.dao.ConnectionDao
import warlockfe.warlock3.core.prefs.dao.ConnectionSettingDao
import warlockfe.warlock3.core.prefs.dao.MacroDao
import warlockfe.warlock3.core.prefs.dao.PresetStyleDao
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
import warlockfe.warlock3.core.prefs.models.AliasEntity
import warlockfe.warlock3.core.prefs.models.AlterationEntity
import warlockfe.warlock3.core.prefs.models.CharacterEntity
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity
import warlockfe.warlock3.core.prefs.models.ClientSettingEntity
import warlockfe.warlock3.core.prefs.models.ConnectionEntity
import warlockfe.warlock3.core.prefs.models.ConnectionSettingEntity
import warlockfe.warlock3.core.prefs.models.MacroEntity
import warlockfe.warlock3.core.prefs.models.PresetStyleEntity
import warlockfe.warlock3.core.prefs.models.ScriptDirEntity
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import kotlin.uuid.Uuid

/** Resolution applied to a character whose settings are being imported. */
enum class ImportMode {
    /** Keep existing rows; overlay the imported ones (overwriting only on key match). */
    MERGE,

    /** Clear all of the target character's existing settings first, then insert the imported set. */
    REPLACE,
}

/** Pseudo-character id used for settings that aren't tied to a specific character. */
private const val GLOBAL_CHARACTER_ID = "global"

class ExportRepository(
    private val accountDao: AccountDao,
    private val aliasDao: AliasDao,
    private val alterationDao: AlterationDao,
    private val characterDao: CharacterDao,
    private val characterSettingDao: CharacterSettingDao,
    private val clientSettingDao: ClientSettingDao,
    private val connectionDao: ConnectionDao,
    private val connectionSettingDao: ConnectionSettingDao,
    private val macroDao: MacroDao,
    private val presetStyleDao: PresetStyleDao,
    private val scriptDirDao: ScriptDirDao,
    private val windowSettingsDao: WindowSettingsDao,
    // Highlights, names, and variables live in the human-editable config files, not the database.
    private val characterConfigStore: CharacterConfigStore,
) {
    // region Export

    suspend fun getExport(): WarlockExport {
        val characters =
            characterDao.getAll() +
                CharacterEntity(id = GLOBAL_CHARACTER_ID, name = GLOBAL_CHARACTER_ID, gameCode = GLOBAL_CHARACTER_ID)
        return WarlockExport(
            accounts = accountDao.getAll().map { AccountExport(username = it.username, password = null) },
            characters = characters.map { buildCharacterExport(it) },
            connections =
                connectionDao.getAllWithSettings().map { connection ->
                    ConnectionExport(
                        id = connection.connection.id,
                        name = connection.connection.name,
                        username = connection.connection.username,
                        gameCode = connection.connection.gameCode,
                        character = connection.connection.character,
                        settings = connection.settings.associate { it.key to it.value },
                    )
                },
            settings = clientSettingDao.getAll().mapNotNull { setting -> setting.value?.let { setting.key to it } }.toMap(),
        )
    }

    suspend fun getCharacterExport(characterId: String): CharacterExport {
        val character =
            characterDao.getById(characterId)
                ?: CharacterEntity(id = characterId, name = characterId, gameCode = GLOBAL_CHARACTER_ID)
        return buildCharacterExport(character)
    }

    private suspend fun buildCharacterExport(character: CharacterEntity): CharacterExport =
        CharacterExport(
            id = character.id,
            name = character.name,
            gameCode = character.gameCode,
            scriptDirectories = scriptDirDao.getByCharacter(character.id),
            settings = characterSettingDao.getByCharacter(character.id).associate { it.key to it.value },
            variables = characterConfigStore.current(character.id).variables,
            aliases =
                aliasDao.getByCharacter(character.id).map {
                    AliasExport(pattern = it.pattern, replacement = it.replacement)
                },
            alterations =
                alterationDao.getAlterationsByCharacter(character.id).map {
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
                characterConfigStore.current(character.id).highlights.map { highlight ->
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
                                        fontFamily = style.fontFamily,
                                        fontSize = style.fontSize,
                                        fontWeight = style.fontWeight,
                                    )
                            },
                    )
                },
            macros =
                macroDao.getByCharacter(character.id).map {
                    MacroExport(key = it.key, value = it.value)
                },
            names =
                characterConfigStore.current(character.id).names.map {
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
                                fontFamily = it.fontFamily,
                                fontSize = it.fontSize,
                                fontWeight = it.fontWeight,
                                entireLine = false,
                            ),
                    )
                },
            presets =
                presetStyleDao.getByCharacter(character.id).map { preset ->
                    PresetStyleExport(
                        id = preset.presetId,
                        style =
                            StyleExport(
                                textColor = preset.textColor,
                                backgroundColor = preset.backgroundColor,
                                bold = preset.bold,
                                italic = preset.italic,
                                underline = preset.underline,
                                fontFamily = preset.fontFamily,
                                fontSize = preset.fontSize,
                                fontWeight = preset.fontWeight,
                                entireLine = preset.entireLine,
                            ),
                    )
                },
            windows =
                windowSettingsDao.getByCharacter(character.id).map {
                    WindowSettingsExport(
                        name = it.name,
                        width = it.width,
                        height = it.height,
                        location = it.location,
                        position = it.position,
                        textColor = it.textColor,
                        backgroundColor = it.backgroundColor,
                        fontFamily = it.fontFamily,
                        fontSize = it.fontSize,
                        fontWeight = it.fontWeight,
                        nameFilter = it.nameFilter,
                    )
                },
        )

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
            connectionDao.save(
                ConnectionEntity(
                    id = connection.id,
                    username = connection.username,
                    gameCode = connection.gameCode,
                    character = connection.character,
                    name = connection.name,
                ),
            )
            connection.settings.forEach { (key, value) ->
                connectionSettingDao.save(ConnectionSettingEntity(connectionId = connection.id, key = key, value = value))
            }
        }
        export.settings.forEach { (key, value) -> clientSettingDao.save(ClientSettingEntity(key = key, value = value)) }
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

    private suspend fun applyCharacter(
        targetCharacterId: String,
        data: CharacterExport,
        mode: ImportMode,
        createCharacterRow: Boolean,
    ) {
        if (createCharacterRow && targetCharacterId != GLOBAL_CHARACTER_ID) {
            characterDao.save(CharacterEntity(id = targetCharacterId, gameCode = data.gameCode, name = data.name))
        }

        if (mode == ImportMode.REPLACE) {
            aliasDao.deleteByCharacter(targetCharacterId)
            alterationDao.deleteByCharacter(targetCharacterId)
            macroDao.deleteByCharacter(targetCharacterId)
            scriptDirDao.deleteByCharacter(targetCharacterId)
            presetStyleDao.deleteByCharacter(targetCharacterId)
            characterSettingDao.deleteByCharacter(targetCharacterId)
            windowSettingsDao.deleteByCharacter(targetCharacterId)
            // highlights/names/variables are cleared and rewritten via the config store below
        }

        // Aliases/alterations have no natural unique key, so when merging we reuse the existing row's
        // id for a matching pattern to overwrite rather than duplicate it.
        val existingAliasIds =
            if (mode == ImportMode.MERGE) {
                aliasDao.getByCharacter(targetCharacterId).associate { it.pattern to it.id }
            } else {
                emptyMap()
            }
        data.aliases.forEach {
            aliasDao.save(
                AliasEntity(
                    id = existingAliasIds[it.pattern] ?: Uuid.random(),
                    characterId = targetCharacterId,
                    pattern = it.pattern,
                    replacement = it.replacement,
                ),
            )
        }

        val existingAlterationIds =
            if (mode == ImportMode.MERGE) {
                alterationDao.getAlterationsByCharacter(targetCharacterId).associate { it.pattern to it.id }
            } else {
                emptyMap()
            }
        data.alterations.forEach {
            alterationDao.save(
                AlterationEntity(
                    id = existingAlterationIds[it.pattern] ?: Uuid.random(),
                    characterId = targetCharacterId,
                    pattern = it.pattern,
                    sourceStream = it.sourceStream,
                    destinationStream = it.destinationStream,
                    result = it.result,
                    ignoreCase = it.ignoreCase,
                    keepOriginal = it.keepOriginal,
                ),
            )
        }

        // Highlights, names, and variables live in the config store. On REPLACE we swap the sections
        // wholesale; on MERGE we overlay, deduping highlights by pattern and names by text.
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
                                fontFamily = style.fontFamily,
                                fontSize = style.fontSize,
                                fontWeight = style.fontWeight,
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
                    fontFamily = name.style.fontFamily,
                    fontSize = name.style.fontSize,
                    fontWeight = name.style.fontWeight,
                )
            }
        characterConfigStore.mutate(targetCharacterId) { current ->
            if (mode == ImportMode.REPLACE) {
                current.copy(
                    highlights = importedHighlights,
                    names = importedNames,
                    variables = data.variables,
                )
            } else {
                val importedPatterns = importedHighlights.mapTo(mutableSetOf()) { it.pattern }
                val importedTexts = importedNames.mapTo(mutableSetOf()) { it.text }
                current.copy(
                    highlights = current.highlights.filterNot { it.pattern in importedPatterns } + importedHighlights,
                    names = current.names.filterNot { it.text in importedTexts } + importedNames,
                    variables = current.variables + data.variables,
                )
            }
        }

        data.macros.forEach { macro ->
            // `key` is the macro's identity; drop any existing row for it so a UI-created row (which
            // carries legacy keyCode/modifier values in its primary key) isn't left as a duplicate.
            macroDao.deleteByKey(characterId = targetCharacterId, key = macro.key)
            @Suppress("DEPRECATION")
            macroDao.save(
                MacroEntity(
                    characterId = targetCharacterId,
                    key = macro.key,
                    value = macro.value,
                    keyCode = 0,
                    ctrl = false,
                    alt = false,
                    shift = false,
                    meta = false,
                ),
            )
        }

        data.presets.forEach { preset ->
            presetStyleDao.save(
                PresetStyleEntity(
                    presetId = preset.id,
                    characterId = targetCharacterId,
                    textColor = preset.style.textColor,
                    backgroundColor = preset.style.backgroundColor,
                    entireLine = preset.style.entireLine,
                    bold = preset.style.bold,
                    italic = preset.style.italic,
                    underline = preset.style.underline,
                    fontFamily = preset.style.fontFamily,
                    fontSize = preset.style.fontSize,
                    fontWeight = preset.style.fontWeight,
                ),
            )
        }

        data.settings.forEach { (key, value) ->
            characterSettingDao.save(CharacterSettingEntity(characterId = targetCharacterId, key = key, value = value))
        }

        data.scriptDirectories.forEach { path ->
            scriptDirDao.save(ScriptDirEntity(characterId = targetCharacterId, path = path))
        }

        data.windows.forEach { window ->
            windowSettingsDao.save(
                WindowSettingsEntity(
                    characterId = targetCharacterId,
                    name = window.name,
                    width = window.width,
                    height = window.height,
                    location = window.location,
                    position = window.position,
                    textColor = window.textColor,
                    backgroundColor = window.backgroundColor,
                    fontFamily = window.fontFamily,
                    fontSize = window.fontSize,
                    fontWeight = window.fontWeight,
                    nameFilter = window.nameFilter,
                ),
            )
        }
    }

    // endregion
}
