package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text
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
 * Desktop (Jewel) settings dispatcher. All pages except Appearance are rewritten in Jewel.
 * Appearance is intentionally not migrated yet — it depends on the preset chain (preview rendering
 * with `StreamTextLine`, color/font preset editing) which crosses into other unmigrated UI.
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
    @Suppress("UNUSED_PARAMETER") presetRepository: PresetRepository,
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
            NotYetImplementedPlaceholder(
                title = "Appearance",
                detail =
                    "Appearance preset editor (per-stream colors and fonts) is not yet ported to the Jewel " +
                        "desktop UI. Use the per-window settings dialog from a stream's title bar to edit individual " +
                        "stream styles.",
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

@Composable
private fun NotYetImplementedPlaceholder(
    title: String,
    detail: String,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title)
            Text(detail)
        }
    }
}
