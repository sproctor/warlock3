package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.compose.ui.settings.VariablesViewModel
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.VariableEntity

@Composable
fun DesktopVariablesView(
    viewModel: VariablesViewModel,
    characters: List<GameCharacter>,
    modifier: Modifier = Modifier,
) {
    val currentCharacter by viewModel.currentCharacter.collectAsState()
    val variables by viewModel.variables.collectAsState()
    var editingVariable by remember { mutableStateOf<VariableEntity?>(null) }
    val character = currentCharacter
    if (character == null) {
        Text("No characters created")
        return
    }
    val characterId = character.id

    SettingsListScaffold(
        selectedCharacter = character,
        characters = characters,
        onSelectCharacter = viewModel::selectCharacter,
        modifier = modifier.fillMaxSize(),
        allowGlobal = false,
    ) {
        WarlockScrollableColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            variables.forEach { variable ->
                WarlockListItem(
                    headline = {
                        Column {
                            Text(variable.name)
                            Text(variable.value)
                        }
                    },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            WarlockOutlinedButton(
                                onClick = { editingVariable = variable },
                                text = "Edit",
                            )
                            WarlockOutlinedButton(
                                onClick = { viewModel.delete(variable) },
                                text = "Delete",
                            )
                        }
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            WarlockButton(
                onClick = { editingVariable = VariableEntity(characterId, "", "") },
                text = "New variable",
            )
        }
    }
    editingVariable?.let { variable ->
        DesktopEditVariableDialog(
            name = variable.name,
            value = variable.value,
            saveVariable = { name, value ->
                viewModel.save(variable, name, value) { editingVariable = null }
            },
            onClose = { editingVariable = null },
        )
    }
}

@Composable
private fun DesktopEditVariableDialog(
    name: String,
    value: String,
    saveVariable: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    val newName = rememberTextFieldState(name)
    val newValue = rememberTextFieldState(value)
    WarlockDialog(
        title = "Edit Variable",
        onCloseRequest = onClose,
        width = 400.dp,
        height = 280.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Name")
            WarlockTextField(state = newName, modifier = Modifier.fillMaxWidth())
            Text("Value")
            WarlockTextField(state = newValue, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = onClose, text = "Cancel")
                WarlockButton(
                    onClick = { saveVariable(newName.text.toString(), newValue.text.toString()) },
                    text = "OK",
                )
            }
        }
    }
}
