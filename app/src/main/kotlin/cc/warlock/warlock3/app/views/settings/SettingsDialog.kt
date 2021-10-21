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
import com.uchuhimo.konf.Config

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SettingsDialog(
    currentCharacter: String?,
    config: Config,
    updateConfig: ((Config) -> Unit) -> Unit,
    variableRegistry: VariableRegistry,
    closeDialog: () -> Unit,
) {
    Window(
        title = "Settings",
        onCloseRequest = closeDialog,
    ) {
        var state: SettingsState by remember { mutableStateOf(VariableSettingsState) }

        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(200.dp).fillMaxHeight()) {
                TextButton(onClick = { state = VariableSettingsState }) {
                    Text("Variables")
                }
                TextButton(onClick = { state = MacroSettingsState }) {
                    Text("Macros")
                }
            }
            when (state) {
                VariableSettingsState -> {
                    val initialCharacter = currentCharacter ?: config[ClientSpec.characters].firstOrNull()?.characterName
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
            }
        }
    }
}

sealed class SettingsState
object VariableSettingsState : SettingsState()
object MacroSettingsState : SettingsState()
