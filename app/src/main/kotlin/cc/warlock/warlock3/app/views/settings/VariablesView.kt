package cc.warlock.warlock3.app.views.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import cc.warlock.warlock3.app.WarlockIcons
import cc.warlock.warlock3.core.prefs.VariableRepository
import cc.warlock.warlock3.core.prefs.models.GameCharacter
import cc.warlock.warlock3.core.prefs.models.Variable
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun VariablesView(
    initialCharacter: GameCharacter,
    characters: List<GameCharacter>,
    variableRepository: VariableRepository,
) {
    var currentCharacter by remember(initialCharacter) { mutableStateOf(initialCharacter) }
    val characterId = currentCharacter.id
    var editingVariable by remember { mutableStateOf<Variable?>(null) }
    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacter = it!! }
        )
        val variables by variableRepository.observeCharacterVariables(characterId).collectAsState(emptyList())
        Column(Modifier.weight(1f).fillMaxHeight()) {
            variables.forEach { variable ->
                ListItem(
                    text = { Text(variable.name) },
                    secondaryText = { Text(variable.value) },
                    trailing = {
                        IconButton(
                            onClick = { editingVariable = variable }
                        ) {
                            Icon(imageVector = WarlockIcons.Edit, contentDescription = "edit")
                        }
                    }
                )
            }
        }
        Row {
            IconButton(
                onClick = { editingVariable = Variable("", "") }
            ) {
                Icon(imageVector = WarlockIcons.Add, contentDescription = "add")
            }
        }
    }
    editingVariable?.let { variable ->
        EditVariableDialog(
            name = variable.name,
            value = variable.value,
            saveVariable = { name, value ->
                runBlocking {
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
    Dialog(onCloseRequest = onClose) {
        Column {
            var newName by remember(name) { mutableStateOf(name) }
            var newValue by remember(value) { mutableStateOf(value) }
            TextField(value = newName, label = { Text("Name") }, onValueChange = { newName = it })
            TextField(value = newValue, label = { Text("Value") }, onValueChange = { newValue = it })
            Row {
                Button(onClick = { saveVariable(newName, newValue) }) {
                    Text("OK")
                }
                Button(onClick = onClose) {
                    Text("CANCEL")
                }
            }
        }
    }
}