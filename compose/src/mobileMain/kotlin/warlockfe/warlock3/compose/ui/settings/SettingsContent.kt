package warlockfe.warlock3.compose.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.repositories.AccountRepository
import warlockfe.warlock3.core.prefs.repositories.ActionRepository
import warlockfe.warlock3.core.prefs.repositories.AliasRepository
import warlockfe.warlock3.core.prefs.repositories.AlterationRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
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
    characterSettingsRepository: CharacterSettingsRepository,
    characterRepository: CharacterRepository,
    scriptDirRepository: ScriptDirRepository,
    variableRepository: VariableRepository,
    macroRepository: MacroRepository,
    highlightRepository: HighlightRepositoryImpl,
    nameRepository: NameRepositoryImpl,
    presetRepository: PresetRepository,
    aliasRepository: AliasRepository,
    actionRepository: ActionRepository,
    alterationRepository: AlterationRepository,
    clientSettingRepository: ClientSettingRepository,
    accountRepository: AccountRepository,
    wraythImporter: WraythImporter,
    windowSettingRepository: WindowSettingsRepository,
    initialWindowTarget: String? = null,
    windowLiveContext: WindowSettingsLiveContext? = null,
) {
    val characters by characterRepository.observeAllCharacters().collectAsState(emptyList())

    when (page) {
        SettingsPage.General -> {
            GeneralSettingsView(
                characterSettingsRepository = characterSettingsRepository,
                initialCharacter = currentCharacter,
                characters = characters,
                scriptDirRepository = scriptDirRepository,
                clientSettingRepository = clientSettingRepository,
                wraythImporter = wraythImporter,
            )
        }

        SettingsPage.Variables -> {
            VariablesView(
                initialCharacter = currentCharacter,
                characters = characters,
                variableRepository = variableRepository,
            )
        }

        SettingsPage.Macros -> {
            MacrosView(
                initialCharacter = currentCharacter,
                characters = characters,
                macroRepository = macroRepository,
            )
        }

        SettingsPage.Highlights -> {
            HighlightsView(
                currentCharacter = currentCharacter,
                allCharacters = characters,
                highlightRepository = highlightRepository,
            )
        }

        SettingsPage.Names -> {
            NamesView(
                currentCharacter = currentCharacter,
                allCharacters = characters,
                nameRepository = nameRepository,
            )
        }

        SettingsPage.Presets -> {
            PresetsView(
                presetRepository = presetRepository,
                characterSettingsRepository = characterSettingsRepository,
                initialCharacter = currentCharacter,
                characters = characters,
            )
        }

        SettingsPage.Windows -> {
            WindowsView(
                initialCharacter = currentCharacter,
                characters = characters,
                presetRepository = presetRepository,
                windowSettingRepository = windowSettingRepository,
                windowLiveContext = windowLiveContext,
                initialWindowTarget = initialWindowTarget,
            )
        }

        SettingsPage.Aliases -> {
            AliasView(
                currentCharacter = currentCharacter,
                allCharacters = characters,
                aliasRepository = aliasRepository,
            )
        }

        SettingsPage.Alterations -> {
            AlterationsView(
                currentCharacter = currentCharacter,
                allCharacters = characters,
                alterationRepository = alterationRepository,
            )
        }

        SettingsPage.Actions -> {
            ActionsView(
                currentCharacter = currentCharacter,
                allCharacters = characters,
                actionRepository = actionRepository,
            )
        }

        SettingsPage.Accounts -> {
            AccountsView(
                accountRepository = accountRepository,
            )
        }

        SettingsPage.Characters -> {
            CharactersView(
                allCharacters = characters,
                characterRepository = characterRepository,
            )
        }
    }
}
