package warlockfe.warlock3.compose.ui.settings

import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.repositories.AccountRepository
import warlockfe.warlock3.core.prefs.repositories.ActionRepository
import warlockfe.warlock3.core.prefs.repositories.AliasRepository
import warlockfe.warlock3.core.prefs.repositories.AlterationRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.IgnoreRepository
import warlockfe.warlock3.core.prefs.repositories.MacroRepository
import warlockfe.warlock3.core.prefs.repositories.NameRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.prefs.repositories.ScriptDirRepository
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.prefs.repositories.WindowSettingsRepository
import warlockfe.warlock3.wrayth.settings.WraythImporter

/**
 * Builds the settings pages' view models, so the settings UI on either platform takes this one
 * factory instead of every repository a page might need.
 */
class SettingsViewModelFactory(
    private val characterRepository: CharacterRepository,
    private val characterSettingsRepository: CharacterSettingsRepository,
    private val clientSettingRepository: ClientSettingRepository,
    private val scriptDirRepository: ScriptDirRepository,
    private val variableRepository: VariableRepository,
    private val macroRepository: MacroRepository,
    private val highlightRepository: HighlightRepositoryImpl,
    private val nameRepository: NameRepositoryImpl,
    private val ignoreRepository: IgnoreRepository,
    private val presetRepository: PresetRepository,
    private val aliasRepository: AliasRepository,
    private val actionRepository: ActionRepository,
    private val alterationRepository: AlterationRepository,
    private val accountRepository: AccountRepository,
    private val windowSettingRepository: WindowSettingsRepository,
    private val wraythImporter: WraythImporter,
) {
    fun settings(): SettingsViewModel = SettingsViewModel(characterRepository)

    fun macros(initialCharacter: GameCharacter?): MacrosViewModel = MacrosViewModel(initialCharacter, macroRepository)

    fun general(initialCharacter: GameCharacter?): GeneralSettingsViewModel =
        GeneralSettingsViewModel(
            initialCharacter = initialCharacter,
            characterSettingsRepository = characterSettingsRepository,
            scriptDirRepository = scriptDirRepository,
            clientSettingRepository = clientSettingRepository,
            wraythImporter = wraythImporter,
        )

    fun windows(initialCharacter: GameCharacter?): WindowsViewModel =
        WindowsViewModel(
            initialCharacter = initialCharacter,
            characterRepository = characterRepository,
            characterSettingsRepository = characterSettingsRepository,
            windowSettingRepository = windowSettingRepository,
        )

    fun accounts(): AccountsViewModel = AccountsViewModel(accountRepository)

    fun characters(): CharactersViewModel = CharactersViewModel(characterRepository)
}
