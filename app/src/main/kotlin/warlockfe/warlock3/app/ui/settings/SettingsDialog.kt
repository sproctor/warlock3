package warlockfe.warlock3.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import warlockfe.warlock3.app.ui.components.DrawerMenuItem
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.AliasRepository
import warlockfe.warlock3.core.prefs.AlterationRepository
import warlockfe.warlock3.core.prefs.CharacterRepository
import warlockfe.warlock3.core.prefs.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.MacroRepository
import warlockfe.warlock3.core.prefs.PresetRepository
import warlockfe.warlock3.core.prefs.ScriptDirRepository
import warlockfe.warlock3.core.prefs.VariableRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    currentCharacter: GameCharacter?,
    characterRepository: CharacterRepository,
    variableRepository: VariableRepository,
    macroRepository: MacroRepository,
    presetRepository: PresetRepository,
    highlightRepository: HighlightRepository,
    alterationRepository: AlterationRepository,
    characterSettingsRepository: CharacterSettingsRepository,
    aliasRepository: AliasRepository,
    scriptDirRepository: ScriptDirRepository,
    closeDialog: () -> Unit,
) {
    Window(
        title = "Settings",
        onCloseRequest = closeDialog,
        state = rememberWindowState(width = 900.dp, height = 600.dp)
    ) {
        var state: SettingsPage by remember { mutableStateOf(SettingsPage.General) }
        val characters by characterRepository.observeAllCharacters().collectAsState(emptyList())

        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet {
                    DrawerMenuItem(
                        title = "General",
                        onClick = { state = SettingsPage.General },
                        selected = state == SettingsPage.General
                    )
                    DrawerMenuItem(
                        title = "Appearance",
                        onClick = { state = SettingsPage.Appearance },
                        selected = state == SettingsPage.Appearance
                    )
                    DrawerMenuItem(
                        title = "Variables",
                        onClick = { state = SettingsPage.Variables },
                        selected = state == SettingsPage.Variables
                    )
                    DrawerMenuItem(
                        title = "Macros",
                        onClick = { state = SettingsPage.Macros },
                        selected = state == SettingsPage.Macros
                    )
                    DrawerMenuItem(
                        title = "Highlights",
                        onClick = { state = SettingsPage.Highlights },
                        selected = state == SettingsPage.Highlights
                    )
                    DrawerMenuItem(
                        title = "Aliases",
                        onClick = { state = SettingsPage.Aliases },
                        selected = state == SettingsPage.Aliases
                    )
                    DrawerMenuItem(
                        title = "Text Alterations",
                        onClick = { state = SettingsPage.Alterations },
                        selected = state == SettingsPage.Alterations
                    )
                }
            }
        ) {
            Box(Modifier.padding(16.dp)) {
                when (state) {
                    SettingsPage.General -> {
                        GeneralSettingsView(
                            characterSettingsRepository = characterSettingsRepository,
                            initialCharacter = currentCharacter,
                            characters = characters,
                            scriptDirRepository = scriptDirRepository,
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
        }
    }
}

enum class SettingsPage {
    Variables,
    Macros,
    Highlights,
    Appearance,
    General,
    Aliases,
    Alterations,
}
