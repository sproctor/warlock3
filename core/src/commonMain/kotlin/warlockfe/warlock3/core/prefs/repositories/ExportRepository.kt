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
import warlockfe.warlock3.core.prefs.export.CharacterExport
import warlockfe.warlock3.core.prefs.export.WarlockExport
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
        val accounts = accountDao.getAll()
        val characters = characterDao.getAll() + CharacterEntity(id = "global", name = "global", gameCode = "global")
        val clientSettings = clientSettingDao.getAll()
        return WarlockExport(
            accounts = accounts.map {
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
                    alterations = emptyList(),
                    highlights = emptyList(),
                    macros = emptyList(),
                    names = emptyList(),
                    presets = emptyList(),
                    windows = emptyList(),
                )
            },
            settings = emptyMap(),
        )
    }
}