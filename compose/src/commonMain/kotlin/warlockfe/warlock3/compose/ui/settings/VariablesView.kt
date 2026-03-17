package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.add
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.VariableEntity
import warlockfe.warlock3.core.prefs.repositories.VariableRepository

@Composable
fun VariablesView(
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    variableRepository: VariableRepository,
) {
    val currentCharacterState =
        remember(initialCharacter, characters) {
            mutableStateOf(
                initialCharacter ?: characters.firstOrNull()
            )
        }
    val currentCharacter = currentCharacterState.value
    if (currentCharacter == null) {
        Text("No characters created")
        return
    }
    val characterId = currentCharacter.id
    var editingVariable by remember { mutableStateOf<VariableEntity?>(null) }
    val scope = rememberCoroutineScope()
    val variables by variableRepository.observeCharacterVariables(characterId)
        .collectAsState(emptyList())

    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it }
        )
        Spacer(Modifier.height(16.dp))
        Text(text = "Variables", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        ScrollableColumn(Modifier.weight(1f)) {
            variables.forEach { variable ->
                ListItem(
                    headlineContent = { Text(variable.name) },
                    supportingContent = { Text(variable.value) },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { editingVariable = variable }
                            ) {
                                Icon(painter = painterResource(Res.drawable.edit), contentDescription = "Edit")
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        variableRepository.delete(characterId, variable.name)
                                    }
                                }
                            ) {
                                Icon(painter = painterResource(Res.drawable.delete), contentDescription = "Delete")
                            }
                        }
                    }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            ExtendedFloatingActionButton(
                onClick = { editingVariable = VariableEntity(characterId, "", "") },
                icon = { Icon(painter = painterResource(Res.drawable.add), contentDescription = null) },
                text = { Text("New variable") },
            )
        }
    }
    editingVariable?.let { variable ->
        EditVariableDialog(
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
            onClose = { editingVariable = null }
        )
    }
}

@Composable
fun EditVariableDialog(
    name: String,
    value: String,
    saveVariable: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    val newName = rememberTextFieldState(name)
    val newValue = rememberTextFieldState(value)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit Variable") },
        confirmButton = {
            TextButton(onClick = { saveVariable(newName.text.toString(), newValue.text.toString()) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextField(
                    state = newName,
                    label = { Text("Name") },
                )
                TextField(
                    state = newValue,
                    label = { Text("Value") },
                )
            }
        }
    )
}
