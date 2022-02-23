package cc.warlock.warlock3.app.views.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
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

@OptIn(ExperimentalMaterialApi::class)
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
        val characters by characterRepository.getAllCharacters().collectAsState(emptyList())

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
                }
            }
            Divider(Modifier.fillMaxHeight().width(1.dp))
            when (state) {
                VariableSettingsState -> {
                    val initialCharacter = currentCharacter ?: characters.firstOrNull()
                    if (initialCharacter != null) {
                        VariablesView(
                            initialCharacter = initialCharacter,
                            characters = characters,
                            variableRepository = variableRepository,
                        )
                    } else {
                        Text("No characters have connected")
                    }
                }
                MacroSettingsState -> MacrosView(
                    initialCharacter = currentCharacter,
                    characters = characters,
                    macroRepository = macroRepository,
                )
                HighlightSettingsState -> HighlightsView(
                    currentCharacter = null,
                    allCharacters = characters,
                    highlightRepository = highlightRepository,
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

sealed class SettingsState
object VariableSettingsState : SettingsState()
object MacroSettingsState : SettingsState()
object HighlightSettingsState : SettingsState()
object AppearanceSettingsState : SettingsState()
