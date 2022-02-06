package cc.warlock.warlock3.app.views.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import cc.warlock.warlock3.app.config.ClientSpec
import cc.warlock.warlock3.app.model.GameCharacter
import cc.warlock.warlock3.core.script.VariableRegistry
import cc.warlock.warlock3.core.text.StyleRepository
import com.uchuhimo.konf.Config
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SettingsDialog(
    currentCharacter: GameCharacter?,
    config: StateFlow<Config>,
    updateConfig: ((Config) -> Config) -> Unit,
    variableRegistry: VariableRegistry,
    styleRepository: StyleRepository,
    closeDialog: () -> Unit,
) {
    Window(
        title = "Settings",
        onCloseRequest = closeDialog,
    ) {
        var state: SettingsState by remember { mutableStateOf(AppearanceSettingsState) }
        val characters by config.map { it[ClientSpec.characters] }.collectAsState(config.value[ClientSpec.characters])

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
                            variableRegistry = variableRegistry,
                        )
                    } else {
                        Text("No characters have connected")
                    }
                }
                MacroSettingsState -> MacrosView(
                    initialCharacter = currentCharacter,
                    characters = characters,
                    globalMacros = config.map { it[ClientSpec.globalMacros] }.collectAsState(config.value[ClientSpec.globalMacros]).value,
                    characterMacros = config.map { it[ClientSpec.characterMacros] }.collectAsState(config.value[ClientSpec.characterMacros]).value,
                    saveMacro = { characterId, name, value ->
                        updateConfig { newConfig ->
                            if (characterId == null) {
                                newConfig[ClientSpec.globalMacros] = newConfig[ClientSpec.globalMacros] + (name to value)
                            } else {
                                val newMacros = (newConfig[ClientSpec.characterMacros][characterId] ?: emptyMap()) + (name to value)
                                newConfig[ClientSpec.characterMacros] = newConfig[ClientSpec.characterMacros] + (characterId to newMacros)
                            }
                            newConfig
                        }
                    },
                    deleteMacro = { characterId, name ->
                        updateConfig { newConfig ->
                            if (characterId == null) {
                                newConfig[ClientSpec.globalMacros] = newConfig[ClientSpec.globalMacros] - name
                            } else {
                                val newMacros =
                                    (newConfig[ClientSpec.characterMacros][characterId] ?: emptyMap()) - name
                                newConfig[ClientSpec.characterMacros] =
                                    newConfig[ClientSpec.characterMacros] + (characterId to newMacros)
                            }
                            newConfig
                        }
                    }
                )
                HighlightSettingsState -> HighlightsView(
                    currentCharacter = null,
                    allCharacters = characters,
                    globalHighlights = config.map { it[ClientSpec.globalHighlights] }.collectAsState(emptyList()).value,
                    characterHighlights = config.map { it[ClientSpec.characterHighlights] }.collectAsState(emptyMap()).value,
                    saveHighlight = { characterId, highlight ->
                        updateConfig { newConfig ->
                            if (characterId == null) {
                                newConfig[ClientSpec.globalHighlights] = newConfig[ClientSpec.globalHighlights] + highlight
                            } else {
                                val newHighlights = (newConfig[ClientSpec.characterHighlights][characterId] ?: emptyList()) + highlight
                                newConfig[ClientSpec.characterHighlights] = newConfig[ClientSpec.characterHighlights] + (characterId to newHighlights)
                            }
                            newConfig
                        }
                    },
                    deleteHighlight = { characterId, pattern ->
                        updateConfig { newConfig ->
                            if (characterId == null) {
                                newConfig[ClientSpec.globalHighlights] = newConfig[ClientSpec.globalHighlights].filter { it.pattern != pattern }
                            } else {
                                val newHighlight =
                                    (newConfig[ClientSpec.characterHighlights][characterId] ?: emptyList()).filter { it.pattern != pattern }
                                newConfig[ClientSpec.characterHighlights] =
                                    newConfig[ClientSpec.characterHighlights] + (characterId to newHighlight)
                            }
                            newConfig
                        }
                    }
                )
                AppearanceSettingsState -> {
                    AppearanceView(
                        styleRepository = styleRepository,
                        initialCharacter = currentCharacter,
                        characters = characters,
                        saveStyle = { characterId, name, styleDefinition ->
                            updateConfig { newConfig ->
                                val newStyles = (newConfig[ClientSpec.styles][characterId] ?: emptyMap()) + (name to styleDefinition)
                                newConfig[ClientSpec.styles] = newConfig[ClientSpec.styles] + (characterId to newStyles)
                                newConfig
                            }
                        }
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
