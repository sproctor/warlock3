package warlockfe.warlock3.core.prefs.repositories

import warlockfe.warlock3.core.prefs.dao.AccountDao
import warlockfe.warlock3.core.prefs.dao.AliasDao
import warlockfe.warlock3.core.prefs.dao.AlterationDao
import warlockfe.warlock3.core.prefs.dao.CharacterDao
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.dao.ConnectionDao
import warlockfe.warlock3.core.prefs.dao.HighlightDao
import warlockfe.warlock3.core.prefs.dao.MacroDao
import warlockfe.warlock3.core.prefs.dao.NameDao
import warlockfe.warlock3.core.prefs.dao.PresetStyleDao
import warlockfe.warlock3.core.prefs.dao.ScriptDirDao
import warlockfe.warlock3.core.prefs.dao.VariableDao
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.prefs.export.AccountExport
import warlockfe.warlock3.core.prefs.export.AliasExport
import warlockfe.warlock3.core.prefs.export.AlterationExport
import warlockfe.warlock3.core.prefs.export.CharacterExport
import warlockfe.warlock3.core.prefs.export.HighlightExport
import warlockfe.warlock3.core.prefs.export.MacroExport
import warlockfe.warlock3.core.prefs.export.NameExport
import warlockfe.warlock3.core.prefs.export.PresetStyleExport
import warlockfe.warlock3.core.prefs.export.StyleExport
import warlockfe.warlock3.core.prefs.export.WarlockExport
import warlockfe.warlock3.core.prefs.mappers.toStyleDefinition
import warlockfe.warlock3.core.prefs.models.CharacterEntity

class ExportRepository(
    private val accountDao: AccountDao,
    private val aliasDao: AliasDao,
    private val alterationDao: AlterationDao,
    private val characterDao: CharacterDao,
    private val characterSettingDao: CharacterSettingDao,
    private val clientSettingDao: ClientSettingDao,
    private val connectionDao: ConnectionDao,
    private val highlightDao: HighlightDao,
    private val macroDao: MacroDao,
    private val nameDao: NameDao,
    private val presetStyleDao: PresetStyleDao,
    private val scriptDirDao: ScriptDirDao,
    private val variableDao: VariableDao,
    private val windowSettingsDao: WindowSettingsDao,
) {
    suspend fun getExport(): WarlockExport {
        val characters = characterDao.getAll() + CharacterEntity(id = "global", name = "global", gameCode = "global")
        val clientSettings = clientSettingDao.getAll()
        return WarlockExport(
            accounts = accountDao.getAll().map {
                AccountExport(
                    username = it.username,
                    password = it.password,
                )
            },
            characters = characters.map { character ->
                CharacterExport(
                    id = character.id,
                    name = character.name,
                    gameCode = character.gameCode,
                    scriptDirectories = scriptDirDao.getByCharacter(character.id),
                    settings = characterSettingDao.getByCharacter(character.id).associate {
                        it.key to it.value
                    },
                    variables = variableDao.getByCharacter(character.id).associate {
                        it.name to it.value
                    },
                    aliases = aliasDao.getByCharacter(character.id).map {
                        AliasExport(
                            pattern = it.pattern,
                            replacement = it.replacement,
                        )
                    },
                    alterations = alterationDao.getAlterationsByCharacter(character.id).map {
                        AlterationExport(
                            pattern = it.pattern,
                            sourceStream = it.sourceStream,
                            destinationStream = it.destinationStream,
                            result = it.result,
                            ignoreCase = it.ignoreCase,
                            keepOriginal = it.keepOriginal,
                        )
                    },
                    highlights = highlightDao.getHighlightsByCharacter(character.id).map {
                        HighlightExport(
                            pattern = it.highlight.pattern,
                            isRegex = it.highlight.isRegex,
                            matchPartialWord = it.highlight.matchPartialWord,
                            ignoreCase = it.highlight.ignoreCase,
                            sound = it.highlight.sound,
                            styles = it.styles.associate { style ->
                                style.groupNumber to StyleExport(
                                    textColor = style.textColor,
                                    backgroundColor = style.backgroundColor,
                                    entireLine = style.entireLine,
                                    bold = style.bold,
                                    italic = style.italic,
                                    underline = style.underline,
                                    fontFamily = style.fontFamily,
                                    fontSize = style.fontSize,
                                )
                            }
                        )
                    },
                    macros = macroDao.getByCharacter(character.id).map {
                        MacroExport(
                            value = it.value,
                            keyCode = it.keyCode,
                            ctrl = it.ctrl,
                            alt = it.alt,
                            shift = it.shift,
                            meta = it.meta,
                        )
                    },
                    names = nameDao.getByCharacter(character.id).map {
                        NameExport(
                            text = it.text,
                            sound = it.sound,
                            style = StyleExport(
                                textColor = it.textColor,
                                backgroundColor = it.backgroundColor,
                                bold = it.bold,
                                italic = it.italic,
                                underline = it.underline,
                                fontFamily = it.fontFamily,
                                fontSize = it.fontSize,
                                entireLine = false,
                            )
                        )
                    },
                    presets = presetStyleDao.getByCharacter(character.id).map { preset ->
                        PresetStyleExport(
                            id = preset.presetId,
                            style = StyleExport(
                                textColor = preset.textColor,
                                backgroundColor = preset.backgroundColor,
                                bold = preset.bold,
                                italic = preset.italic,
                                underline = preset.underline,
                                fontFamily = preset.fontFamily,
                                fontSize = preset.fontSize,
                                entireLine = preset.entireLine,
                            )
                        )
                    },
                    windows = emptyList(),
                )
            },
            settings = emptyMap(),
        )
    }
}