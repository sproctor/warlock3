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
import cc.warlock.warlock3.core.script.VariableRegistry

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun VariablesView(
    currentCharacter: String,
    variableRegistry: VariableRegistry,
) {
    var subject by remember { mutableStateOf(currentCharacter) }
    var editingVariable by remember { mutableStateOf<Pair<String, String>?>(null) }
    Column(Modifier.fillMaxSize()) {
        // TODO add character selector
        Text("Showing variables for $subject")
        val allVariables = variableRegistry.variables.collectAsState()
        val variables = allVariables.value[subject.lowercase()] ?: emptyMap()
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
                    variableRegistry.deleteVariable(subject.lowercase(), variable.first)
                }
                variableRegistry.setVariable(subject.lowercase(), name, value)
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