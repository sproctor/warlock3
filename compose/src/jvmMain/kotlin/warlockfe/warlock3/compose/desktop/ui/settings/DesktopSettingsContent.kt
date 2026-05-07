package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import warlockfe.warlock3.compose.ui.settings.AliasView
import warlockfe.warlock3.compose.ui.settings.AlterationsView
import warlockfe.warlock3.compose.ui.settings.AppearanceView
import warlockfe.warlock3.compose.ui.settings.GeneralSettingsView
import warlockfe.warlock3.compose.ui.settings.HighlightsView
import warlockfe.warlock3.compose.ui.settings.MacrosView
import warlockfe.warlock3.compose.ui.settings.NamesView
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

/**
 * Desktop (Jewel) settings dispatcher. Pages with a Jewel rewrite are routed to the new
 * jvmMain implementations; pages that haven't been rewritten yet fall through to the
 * Material3 implementations in commonMain. Material3 stays loaded on desktop so the
 * fall-through pages render correctly during this incremental migration.
 */
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
            // Complex (file pickers, theme selection, logging) — keep on M3 until later step.
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
            DesktopVariablesView(
                initialCharacter = currentCharacter,
                characters = characters,
                variableRepository = variableRepository,
            )
        }
        SettingsPage.Macros ->
            MacrosView(
                initialCharacter = currentCharacter,
                characters = characters,
                macroRepository = macroRepository,
            )
        SettingsPage.Highlights ->
            HighlightsView(
                currentCharacter = currentCharacter,
                allCharacters = characters,
                highlightRepository = highlightRepository,
            )
        SettingsPage.Names ->
            NamesView(
                currentCharacter = currentCharacter,
                allCharacters = characters,
                nameRepository = nameRepository,
            )
        SettingsPage.Appearance -> {
            // Depends on ColorPickerDialog/FontPickerDialog (M3) — keep on M3 for now.
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
