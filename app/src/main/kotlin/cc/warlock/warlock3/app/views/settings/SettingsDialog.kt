package cc.warlock.warlock3.app.views.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import cc.warlock.warlock3.app.config.ClientSpec
import cc.warlock.warlock3.app.util.observe
import cc.warlock.warlock3.core.script.VariableRegistry
import cc.warlock.warlock3.core.text.StyleRepository
import com.uchuhimo.konf.Config

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SettingsDialog(
    currentCharacter: String?,
    config: Config,
    updateConfig: ((Config) -> Unit) -> Unit,
    variableRegistry: VariableRegistry,
    styleRepository: StyleRepository,
    closeDialog: () -> Unit,
) {
    Window(
        title = "Settings",
        onCloseRequest = closeDialog,
    ) {
        var state: SettingsState by remember { mutableStateOf(AppearanceSettingsState) }
        val characters by config.observe(ClientSpec.characters).collectAsState(emptyList())

        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(160.dp).fillMaxHeight()) {
                TextButton(onClick = { state = AppearanceSettingsState }) {
                    Text("Appearance")
                }
                TextButton(onClick = { state = VariableSettingsState }) {
                    Text("Variables")
                }
                TextButton(onClick = { state = MacroSettingsState }) {
                    Text("Macros")
                }
                TextButton(onClick = { state = HighlightSettingsState }) {
                    Text("Highlights")
                }
            }
            when (state) {
                VariableSettingsState -> {
                    val initialCharacter = currentCharacter ?: characters.firstOrNull()?.characterName
                    if (initialCharacter != null) {
                        VariablesView(
                            currentCharacter = initialCharacter,
                            variableRegistry = variableRegistry,
                        )
                    } else {
                        Text("No characters have connected")
                    }
                }
                MacroSettingsState -> MacrosView(
                    currentCharacter = currentCharacter,
                    globalMacros = config.observe(ClientSpec.globalMacros).collectAsState(emptyMap()).value,
                    characterMacros = config.observe(ClientSpec.characterMacros).collectAsState(emptyMap()).value,
                    saveMacro = { characterId, name, value ->
                        updateConfig { newConfig ->
                            if (characterId == null) {
                                newConfig[ClientSpec.globalMacros] = newConfig[ClientSpec.globalMacros] + (name to value)
                            } else {
                                val newMacros = (newConfig[ClientSpec.characterMacros][characterId] ?: emptyMap()) + (name to value)
                                newConfig[ClientSpec.characterMacros] = newConfig[ClientSpec.characterMacros] + (characterId to newMacros)
                            }
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
                        }
                    }
                )
                HighlightSettingsState -> HighlightsView(
                    currentCharacter = currentCharacter,
                    globalHighlights = config.observe(ClientSpec.globalHighlights).collectAsState(emptyList()).value,
                    characterHighlights = config.observe(ClientSpec.characterHighlights).collectAsState(emptyMap()).value,
                    saveHighlight = { characterId, highlight ->
                        updateConfig { newConfig ->
                            if (characterId == null) {
                                newConfig[ClientSpec.globalHighlights] = newConfig[ClientSpec.globalHighlights] + highlight
                            } else {
                                val newHighlights = (newConfig[ClientSpec.characterHighlights][characterId] ?: emptyList()) + highlight
                                newConfig[ClientSpec.characterHighlights] = newConfig[ClientSpec.characterHighlights] + (characterId to newHighlights)
                            }
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
                        }
                    }
                )
                AppearanceSettingsState -> {
                    AppearanceView(
                        styleRepository = styleRepository,
                        currentId = currentCharacter,
                        characters = characters,
                        saveStyle = { characterId, name, styleDefinition ->
                            updateConfig { newConfig ->
                                val newStyles = (newConfig[ClientSpec.styles][characterId] ?: emptyMap()) + (name to styleDefinition)
                                newConfig[ClientSpec.styles] = newConfig[ClientSpec.styles] + (characterId to newStyles)
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
