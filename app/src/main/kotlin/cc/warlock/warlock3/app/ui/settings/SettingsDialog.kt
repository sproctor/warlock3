package cc.warlock.warlock3.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import cc.warlock.warlock3.core.client.GameCharacter
import cc.warlock.warlock3.core.prefs.*

@Composable
fun SettingsDialog(
    currentCharacter: GameCharacter?,
    characterRepository: CharacterRepository,
    variableRepository: VariableRepository,
    macroRepository: MacroRepository,
    presetRepository: PresetRepository,
    highlightRepository: HighlightRepository,
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

        Row(Modifier.fillMaxSize()) {
            Surface(Modifier.padding(8.dp).width(160.dp)) {
                Column(Modifier.fillMaxSize()) {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { state = SettingsPage.General },
                    ) {
                        Text("General")
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { state = SettingsPage.Appearance },
                    ) {
                        Text(text = "Appearance", textAlign = TextAlign.Start)
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { state = SettingsPage.Variables },
                    ) {
                        Text(text = "Variables", textAlign = TextAlign.Start)
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { state = SettingsPage.Macros },
                    ) {
                        Text("Macros")
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { state = SettingsPage.Highlights },
                    ) {
                        Text("Highlights")
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { state = SettingsPage.Aliases }
                    ) {
                        Text("Aliases")
                    }
                }
            }
            Divider(Modifier.fillMaxHeight().width(1.dp))
            Box(Modifier.padding(24.dp)) {
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
}
