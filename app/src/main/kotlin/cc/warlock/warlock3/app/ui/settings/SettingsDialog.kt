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
    closeDialog: () -> Unit,
) {
    Window(
        title = "Settings",
        onCloseRequest = closeDialog,
    ) {
        var state: SettingsState by remember { mutableStateOf(GeneralSettingsState) }
        val characters by characterRepository.observeAllCharacters().collectAsState(emptyList())

        Row(Modifier.fillMaxSize()) {
            Surface(Modifier.padding(8.dp).width(160.dp)) {
                Column(Modifier.fillMaxSize()) {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { state = GeneralSettingsState },
                    ) {
                        Text("General")
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { state = AppearanceSettingsState },
                    ) {
                        Text(text = "Appearance", textAlign = TextAlign.Start)
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { state = VariableSettingsState },
                    ) {
                        Text(text = "Variables", textAlign = TextAlign.Start)
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { state = MacroSettingsState },
                    ) {
                        Text("Macros")
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { state = HighlightSettingsState },
                    ) {
                        Text("Highlights")
                    }
                }
            }
            Divider(Modifier.fillMaxHeight().width(1.dp))
            Box(Modifier.padding(24.dp)) {
                when (state) {
                    GeneralSettingsState -> {
                        GeneralSettingsView(
                            characterSettingsRepository = characterSettingsRepository,
                            initialCharacter = currentCharacter,
                            characters = characters,
                        )
                    }
                    VariableSettingsState -> {
                        VariablesView(
                            initialCharacter = currentCharacter,
                            characters = characters,
                            variableRepository = variableRepository,
                        )
                    }
                    MacroSettingsState -> MacrosView(
                        initialCharacter = currentCharacter,
                        characters = characters,
                        macroRepository = macroRepository,
                    )
                    HighlightSettingsState -> HighlightsView(
                        currentCharacter = currentCharacter,
                        allCharacters = characters,
                        highlightRepository = highlightRepository,
                        presetRepository = presetRepository,
                    )
                    AppearanceSettingsState -> {
                        AppearanceView(
                            presetRepository = presetRepository,
                            initialCharacter = currentCharacter,
                            characters = characters,
                        )
                    }
                }
            }
        }
    }
}

sealed class SettingsState
object VariableSettingsState : SettingsState()
object MacroSettingsState : SettingsState()
object HighlightSettingsState : SettingsState()
object AppearanceSettingsState : SettingsState()
object GeneralSettingsState : SettingsState()
