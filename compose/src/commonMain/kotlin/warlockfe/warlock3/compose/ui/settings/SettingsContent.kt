package warlockfe.warlock3.compose.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.AliasRepository
import warlockfe.warlock3.core.prefs.AlterationRepository
import warlockfe.warlock3.core.prefs.CharacterRepository
import warlockfe.warlock3.core.prefs.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.ClientSettingRepository
import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.MacroRepository
import warlockfe.warlock3.core.prefs.NameRepository
import warlockfe.warlock3.core.prefs.PresetRepository
import warlockfe.warlock3.core.prefs.ScriptDirRepository
import warlockfe.warlock3.core.prefs.VariableRepository

@Composable
fun SettingsContent(
    page: SettingsPage,
    currentCharacter: GameCharacter?,
    characterSettingsRepository: CharacterSettingsRepository,
    characterRepository: CharacterRepository,
    scriptDirRepository: ScriptDirRepository,
    variableRepository: VariableRepository,
    macroRepository: MacroRepository,
    highlightRepository: HighlightRepository,
    nameRepository: NameRepository,
    presetRepository: PresetRepository,
    aliasRepository: AliasRepository,
    alterationRepository: AlterationRepository,
    clientSettingRepository: ClientSettingRepository,
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
            )
        }

        SettingsPage.Variables -> {
            VariablesView(
                initialCharacter = currentCharacter,
                characters = characters,
                variableRepository = variableRepository,
            )
        }

        SettingsPage.Macros -> MacrosView(
            initialCharacter = currentCharacter,
            characters = characters,
            macroRepository = macroRepository,
        )

        SettingsPage.Highlights -> HighlightsView(
            currentCharacter = currentCharacter,
            allCharacters = characters,
            highlightRepository = highlightRepository,
        )

        SettingsPage.Names -> NamesView(
            currentCharacter = currentCharacter,
            allCharacters = characters,
            nameRepository = nameRepository,
        )

        SettingsPage.Appearance -> {
            AppearanceView(
                presetRepository = presetRepository,
                initialCharacter = currentCharacter,
                characters = characters,
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
    }
}