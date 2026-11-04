package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import warlockfe.warlock3.compose.crash.CrashReporting
import warlockfe.warlock3.compose.ui.settings.ProvideSettingsViewModelStore
import warlockfe.warlock3.compose.ui.settings.SettingsPage
import warlockfe.warlock3.compose.ui.settings.SettingsViewModelFactory
import warlockfe.warlock3.compose.ui.settings.WindowSettingsLiveContext
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

@Composable
fun DesktopSettingsContent(
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
                DesktopGeneralSettingsView(
                    characterSettingsRepository = characterSettingsRepository,
                    initialCharacter = currentCharacter,
                    characters = characters,
                    scriptDirRepository = scriptDirRepository,
                    clientSettingRepository = clientSettingRepository,
                )
            }

            SettingsPage.CrashReports -> {
                DesktopCrashReportsView(crashReporting = crashReporting)
            }

            SettingsPage.Variables -> {
                DesktopVariablesView(
                    viewModel = viewModel { settingsViewModelFactory.variables(currentCharacter) },
                    characters = characters,
                )
            }

            SettingsPage.Macros -> {
                DesktopMacrosView(
                    viewModel = viewModel { settingsViewModelFactory.macros(currentCharacter) },
                    characters = characters,
                )
            }

            SettingsPage.Highlights -> {
                DesktopHighlightsView(
                    viewModel = viewModel { settingsViewModelFactory.highlights(currentCharacter) },
                    allCharacters = characters,
                )
            }

            SettingsPage.Names -> {
                DesktopNamesView(
                    viewModel = viewModel { settingsViewModelFactory.names(currentCharacter) },
                    allCharacters = characters,
                )
            }

            SettingsPage.Ignores -> {
                DesktopIgnoresView(
                    viewModel = viewModel { settingsViewModelFactory.ignores(currentCharacter) },
                    allCharacters = characters,
                )
            }

            SettingsPage.TextStyles -> {
                DesktopPresetsView(
                    viewModel = viewModel { settingsViewModelFactory.presets(currentCharacter) },
                    characters = characters,
                )
            }

            SettingsPage.Windows -> {
                DesktopWindowsView(
                    initialCharacter = currentCharacter,
                    characters = characters,
                    characterSettingsRepository = characterSettingsRepository,
                    windowSettingRepository = windowSettingRepository,
                    windowLiveContext = windowLiveContext,
                    initialWindowTarget = initialWindowTarget,
                )
            }

            SettingsPage.Aliases -> {
                DesktopAliasView(
                    viewModel = viewModel { settingsViewModelFactory.aliases(currentCharacter) },
                    allCharacters = characters,
                )
            }

            SettingsPage.Alterations -> {
                DesktopAlterationsView(
                    viewModel = viewModel { settingsViewModelFactory.alterations(currentCharacter) },
                    allCharacters = characters,
                )
            }

            SettingsPage.Actions -> {
                DesktopActionsView(
                    viewModel = viewModel { settingsViewModelFactory.actions(currentCharacter) },
                    allCharacters = characters,
                )
            }

            SettingsPage.Accounts -> {
                DesktopAccountsView(
                    accountRepository = accountRepository,
                )
            }

            SettingsPage.Characters -> {
                DesktopCharactersView(
                    allCharacters = characters,
                    characterRepository = characterRepository,
                )
            }
        }
    }
}
