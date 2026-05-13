package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import warlockfe.warlock3.compose.ui.settings.SettingsPage
import warlockfe.warlock3.core.client.GameCharacter
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
import warlockfe.warlock3.wrayth.settings.WraythImporter

@Composable
fun DesktopSettingsContent(
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
    alterationRepository: AlterationRepository,
    clientSettingRepository: ClientSettingRepository,
    wraythImporter: WraythImporter,
) {
    val characters by characterRepository.observeAllCharacters().collectAsState(emptyList())

    when (page) {
        SettingsPage.General -> {
            DesktopGeneralSettingsView(
                characterSettingsRepository = characterSettingsRepository,
                initialCharacter = currentCharacter,
                characters = characters,
                scriptDirRepository = scriptDirRepository,
                clientSettingRepository = clientSettingRepository,
                wraythImporter = wraythImporter,
            )
        }
        SettingsPage.Variables -> {
            DesktopVariablesView(
                initialCharacter = currentCharacter,
                characters = characters,
                variableRepository = variableRepository,
            )
        }
        SettingsPage.Macros ->
            DesktopMacrosView(
                initialCharacter = currentCharacter,
                characters = characters,
                macroRepository = macroRepository,
            )
        SettingsPage.Highlights ->
            DesktopHighlightsView(
                currentCharacter = currentCharacter,
                allCharacters = characters,
                highlightRepository = highlightRepository,
            )
        SettingsPage.Names ->
            DesktopNamesView(
                currentCharacter = currentCharacter,
                allCharacters = characters,
                nameRepository = nameRepository,
            )
        SettingsPage.Appearance ->
            DesktopAppearanceView(
                initialCharacter = currentCharacter,
                characters = characters,
                presetRepository = presetRepository,
            )
        SettingsPage.Aliases -> {
            DesktopAliasView(
                currentCharacter = currentCharacter,
                allCharacters = characters,
                aliasRepository = aliasRepository,
            )
        }
        SettingsPage.Alterations -> {
            DesktopAlterationsView(
                currentCharacter = currentCharacter,
                allCharacters = characters,
                alterationRepository = alterationRepository,
            )
        }
    }
}
