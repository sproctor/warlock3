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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.VariableEntity
import warlockfe.warlock3.core.prefs.repositories.VariableRepository

@Composable
fun DesktopVariablesView(
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    variableRepository: VariableRepository,
    modifier: Modifier = Modifier,
) {
    val currentCharacterState =
        remember(initialCharacter, characters) {
            mutableStateOf(initialCharacter ?: characters.firstOrNull())
        }
    val currentCharacter = currentCharacterState.value
    if (currentCharacter == null) {
        Text("No characters created")
        return
    }
    val characterId = currentCharacter.id
    var editingVariable by remember { mutableStateOf<VariableEntity?>(null) }
    val scope = rememberCoroutineScope()
    val variables by variableRepository
        .observeCharacterVariables(characterId)
        .collectAsState(emptyList())

    Column(modifier.fillMaxSize()) {
        DesktopSettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it },
        )
        Spacer(Modifier.height(16.dp))
        Text("Variables")
        Spacer(Modifier.height(8.dp))
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
                                onClick = {
                                    scope.launch {
                                        variableRepository.delete(characterId, variable.name)
                                    }
                                },
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
                scope.launch {
                    if (name != variable.name) {
                        variableRepository.delete(characterId, variable.name)
                    }
                    variableRepository.put(characterId, name, value)
                    editingVariable = null
                }
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
