package warlockfe.warlock3.compose.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import warlockfe.warlock3.compose.crash.CrashReporting
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

@Composable
fun SettingsContent(
    page: SettingsPage,
    currentCharacter: GameCharacter?,
    settingsViewModelFactory: SettingsViewModelFactory,
    characterSettingsRepository: CharacterSettingsRepository,
    characterRepository: CharacterRepository,
    scriptDirRepository: ScriptDirRepository,
    variableRepository: VariableRepository,
    macroRepository: MacroRepository,
    highlightRepository: HighlightRepositoryImpl,
    nameRepository: NameRepositoryImpl,
    ignoreRepository: IgnoreRepository,
    presetRepository: PresetRepository,
    aliasRepository: AliasRepository,
    actionRepository: ActionRepository,
    alterationRepository: AlterationRepository,
    clientSettingRepository: ClientSettingRepository,
    accountRepository: AccountRepository,
    wraythImporter: WraythImporter,
    windowSettingRepository: WindowSettingsRepository,
    crashReporting: CrashReporting,
    initialWindowTarget: String? = null,
    windowLiveContext: WindowSettingsLiveContext? = null,
) {
    ProvideSettingsViewModelStore {
        SettingsPageContent(
            page = page,
            currentCharacter = currentCharacter,
            settingsViewModelFactory = settingsViewModelFactory,
            characterSettingsRepository = characterSettingsRepository,
            characterRepository = characterRepository,
            scriptDirRepository = scriptDirRepository,
            variableRepository = variableRepository,
            highlightRepository = highlightRepository,
            nameRepository = nameRepository,
            ignoreRepository = ignoreRepository,
            presetRepository = presetRepository,
            aliasRepository = aliasRepository,
            actionRepository = actionRepository,
            alterationRepository = alterationRepository,
            clientSettingRepository = clientSettingRepository,
            accountRepository = accountRepository,
            wraythImporter = wraythImporter,
            windowSettingRepository = windowSettingRepository,
            crashReporting = crashReporting,
            initialWindowTarget = initialWindowTarget,
            windowLiveContext = windowLiveContext,
        )
    }
}

@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    currentCharacter: GameCharacter?,
    settingsViewModelFactory: SettingsViewModelFactory,
    characterSettingsRepository: CharacterSettingsRepository,
    characterRepository: CharacterRepository,
    scriptDirRepository: ScriptDirRepository,
    variableRepository: VariableRepository,
    highlightRepository: HighlightRepositoryImpl,
    nameRepository: NameRepositoryImpl,
    ignoreRepository: IgnoreRepository,
    presetRepository: PresetRepository,
    aliasRepository: AliasRepository,
    actionRepository: ActionRepository,
    alterationRepository: AlterationRepository,
    clientSettingRepository: ClientSettingRepository,
    accountRepository: AccountRepository,
    wraythImporter: WraythImporter,
    windowSettingRepository: WindowSettingsRepository,
    crashReporting: CrashReporting,
    initialWindowTarget: String?,
    windowLiveContext: WindowSettingsLiveContext?,
) {
    val settingsViewModel = viewModel { settingsViewModelFactory.settings() }
    val characters by settingsViewModel.characters.collectAsState()

    // Each page's view models live as long as the page is showing.
    ProvideSettingsViewModelStore(page, currentCharacter) {
        when (page) {
            SettingsPage.General -> {
                GeneralSettingsView(
                    viewModel = viewModel { settingsViewModelFactory.general(currentCharacter) },
                    characters = characters,
                )
            }

            SettingsPage.CrashReports -> {
                CrashReportsView(crashReporting = crashReporting)
            }

            SettingsPage.Variables -> {
                VariablesView(
                    viewModel = viewModel { settingsViewModelFactory.variables(currentCharacter) },
                    characters = characters,
                )
            }

            SettingsPage.Macros -> {
                MacrosView(
                    viewModel = viewModel { settingsViewModelFactory.macros(currentCharacter) },
                    characters = characters,
                )
            }

            SettingsPage.Highlights -> {
                HighlightsView(
                    viewModel = viewModel { settingsViewModelFactory.highlights(currentCharacter) },
                    allCharacters = characters,
                )
            }

            SettingsPage.Names -> {
                NamesView(
                    viewModel = viewModel { settingsViewModelFactory.names(currentCharacter) },
                    allCharacters = characters,
                )
            }

            SettingsPage.Ignores -> {
                IgnoresView(
                    viewModel = viewModel { settingsViewModelFactory.ignores(currentCharacter) },
                    allCharacters = characters,
                )
            }

            SettingsPage.TextStyles -> {
                PresetsView(
                    viewModel = viewModel { settingsViewModelFactory.presets(currentCharacter) },
                    characters = characters,
                )
            }

            SettingsPage.Windows -> {
                WindowsView(
                    viewModel = viewModel { settingsViewModelFactory.windows(currentCharacter) },
                    characters = characters,
                    windowLiveContext = windowLiveContext,
                    initialWindowTarget = initialWindowTarget,
                )
            }

            SettingsPage.Aliases -> {
                AliasView(
                    viewModel = viewModel { settingsViewModelFactory.aliases(currentCharacter) },
                    allCharacters = characters,
                )
            }

            SettingsPage.Alterations -> {
                AlterationsView(
                    viewModel = viewModel { settingsViewModelFactory.alterations(currentCharacter) },
                    allCharacters = characters,
                )
            }

            SettingsPage.Actions -> {
                ActionsView(
                    viewModel = viewModel { settingsViewModelFactory.actions(currentCharacter) },
                    allCharacters = characters,
                )
            }

            SettingsPage.Accounts -> {
                AccountsView(
                    viewModel = viewModel { settingsViewModelFactory.accounts() },
                )
            }

            SettingsPage.Characters -> {
                CharactersView(
                    viewModel = viewModel { settingsViewModelFactory.characters() },
                    allCharacters = characters,
                )
            }
        }
    }
}
