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
import cc.warlock.warlock3.core.prefs.CharacterRepository
import cc.warlock.warlock3.core.prefs.HighlightRepository
import cc.warlock.warlock3.core.prefs.MacroRepository
import cc.warlock.warlock3.core.prefs.VariableRepository
import cc.warlock.warlock3.core.prefs.models.GameCharacter
import cc.warlock.warlock3.core.prefs.models.PresetRepository

@Composable
fun SettingsDialog(
    currentCharacter: GameCharacter?,
    characterRepository: CharacterRepository,
    variableRepository: VariableRepository,
    macroRepository: MacroRepository,
    presetRepository: PresetRepository,
    highlightRepository: HighlightRepository,
    closeDialog: () -> Unit,
) {
    Window(
        title = "Settings",
        onCloseRequest = closeDialog,
    ) {
        var state: SettingsState by remember { mutableStateOf(AppearanceSettingsState) }
        val characters by characterRepository.observeAllCharacters().collectAsState(emptyList())

        Row(Modifier.fillMaxSize()) {
            Surface(Modifier.padding(8.dp).width(160.dp)) {
                Column(Modifier.fillMaxSize()) {
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
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { state = ScriptSettingsState },
                    ) {
                        Text("Scripting")
                    }
                }
            }
            Divider(Modifier.fillMaxHeight().width(1.dp))
            val currentOrFirstCharacter = currentCharacter ?: characters.firstOrNull()
            Box(Modifier.padding(24.dp)) {
                when (state) {
                    VariableSettingsState -> {
                        VariablesView(
                            initialCharacter = currentOrFirstCharacter,
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
                    )
                    AppearanceSettingsState -> {
                        AppearanceView(
                            presetRepository = presetRepository,
                            initialCharacter = currentOrFirstCharacter,
                            characters = characters,
                        )
                    }
                    ScriptSettingsState -> {

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
object ScriptSettingsState : SettingsState()
