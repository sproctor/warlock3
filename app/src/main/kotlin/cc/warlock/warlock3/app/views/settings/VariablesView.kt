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
import cc.warlock.warlock3.app.model.GameCharacter
import cc.warlock.warlock3.core.script.VariableRegistry

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun VariablesView(
    initialCharacter: GameCharacter,
    characters: List<GameCharacter>,
    variableRegistry: VariableRegistry,
) {
    var currentCharacter by remember(initialCharacter) { mutableStateOf(initialCharacter) }
    val characterKey = currentCharacter.key
    var editingVariable by remember { mutableStateOf<Pair<String, String>?>(null) }
    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacter = it!! }
        )
        val allVariables = variableRegistry.variables.collectAsState(initial = emptyMap())
        val variables = allVariables.value[characterKey] ?: emptyMap()
        Column(Modifier.weight(1f).fillMaxHeight()) {
            variables.forEach { entry ->
                ListItem(
                    text = { Text(entry.key) },
                    secondaryText = { Text(entry.value) },
                    trailing = {
                        IconButton(
                            onClick = { editingVariable = entry.toPair() }
                        ) {
                            Icon(imageVector = WarlockIcons.Edit, contentDescription = "edit")
                        }
                    }
                )
            }
        }
        Row {
            IconButton(
                onClick = { editingVariable = Pair("", "") }
            ) {
                Icon(imageVector = WarlockIcons.Add, contentDescription = "add")
            }
        }
    }
    editingVariable?.let { variable ->
        EditVariableDialog(
            name = variable.first,
            value = variable.second,
            saveVariable = { name, value ->
                if (name != variable.first) {
                    variableRegistry.deleteVariable(characterKey, variable.first)
                }
                variableRegistry.saveVariable(characterKey, name, value)
                editingVariable = null
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