package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.prefs.models.VariableEntity

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
        Text("no characters created")
        return
    }
    val characterId = currentCharacter.id
    var editingVariable by remember { mutableStateOf<VariableEntity?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it }
        )
        Spacer(Modifier.height(16.dp))
        Text(text = "Variables", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        val variables by variableRepository.observeCharacterVariables(characterId)
            .collectAsState(emptyList())
        ScrollableColumn(Modifier.weight(1f)) {
            variables.forEach { variable ->
                ListItem(
                    headlineContent = { Text(variable.name) },
                    supportingContent = { Text(variable.value) },
                    trailingContent = {
                        IconButton(
                            onClick = { editingVariable = variable }
                        ) {
                            Icon(painter = painterResource(Res.drawable.edit), contentDescription = "edit")
                        }
                    }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = { editingVariable = VariableEntity(characterId, "", "") }
            ) {
                Icon(painter = painterResource(Res.drawable.add), contentDescription = "add")
            }
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
    var newName by remember(name) { mutableStateOf(name) }
    var newValue by remember(value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit Variable") },
        confirmButton = {
            TextButton(onClick = { saveVariable(newName, newValue) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel")
            }
        },
        text = {
            Column(Modifier.padding(24.dp)) {
                TextField(
                    value = newName,
                    label = { Text("Name") },
                    onValueChange = { newName = it })
                Spacer(Modifier.height(16.dp))
                TextField(
                    value = newValue,
                    label = { Text("Value") },
                    onValueChange = { newValue = it })
            }
        }
    )
}