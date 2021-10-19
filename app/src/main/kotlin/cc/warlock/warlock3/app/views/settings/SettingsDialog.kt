package cc.warlock.warlock3.app.views.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import cc.warlock.warlock3.app.views.settings.MacrosView
import cc.warlock.warlock3.app.views.settings.VariablesView

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SettingsDialog(
    variables: Map<String, String>,
    saveVariable: (String, String) -> Unit,
    deleteVariable: (String) -> Unit,
    macros: Map<String, String>,
    saveMacro: (String, String) -> Unit,
    deleteMacro: (String) -> Unit,
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
                VariableSettingsState -> VariablesView(
                    variables = variables,
                    saveVariable = saveVariable,
                    deleteVariable = deleteVariable,
                )
                MacroSettingsState -> MacrosView(macros = macros, saveMacro = saveMacro, deleteMacro = deleteMacro)
            }
        }
    }
}

sealed class SettingsState
object VariableSettingsState : SettingsState()
object MacroSettingsState : SettingsState()
