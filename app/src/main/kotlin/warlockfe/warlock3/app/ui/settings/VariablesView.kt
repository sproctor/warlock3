package warlockfe.warlock3.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import warlockfe.warlock3.app.ui.theme.WarlockIcons
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.prefs.models.Variable
import kotlinx.coroutines.runBlocking

@Composable
fun VariablesView(
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    variableRepository: VariableRepository,
) {
    val currentCharacterState =
        remember(initialCharacter, characters) { mutableStateOf(initialCharacter ?: characters.firstOrNull()) }
    val currentCharacter = currentCharacterState.value
    if (currentCharacter == null) {
        Text("no characters created")
        return
    }
    val characterId = currentCharacter.id
    var editingVariable by remember { mutableStateOf<Variable?>(null) }
    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it }
        )
        Spacer(Modifier.height(16.dp))
        Text(text = "Variables", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        val variables by variableRepository.observeCharacterVariables(characterId).collectAsState(emptyList())
        Box(Modifier.weight(1f)) {
            val scrollState = rememberScrollState()
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(end = LocalScrollbarStyle.current.thickness)
                    .verticalScroll(scrollState)
            ) {
                variables.forEach { variable ->
                    ListItem(
                        headlineContent = { Text(variable.name) },
                        supportingContent = { Text(variable.value) },
                        trailingContent = {
                            IconButton(
                                onClick = { editingVariable = variable }
                            ) {
                                Icon(imageVector = WarlockIcons.Edit, contentDescription = "edit")
                            }
                        }
                    )
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd),
                adapter = rememberScrollbarAdapter(scrollState)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
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
    DialogWindow(
        onCloseRequest = onClose,
        title = "Edit Variable"
    ) {
        Column(Modifier.padding(24.dp)) {
            var newName by remember(name) { mutableStateOf(name) }
            var newValue by remember(value) { mutableStateOf(value) }
            TextField(value = newName, label = { Text("Name") }, onValueChange = { newName = it })
            Spacer(Modifier.height(16.dp))
            TextField(value = newValue, label = { Text("Value") }, onValueChange = { newValue = it })
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = { saveVariable(newName, newValue) }) {
                    Text("OK")
                }
                Spacer(Modifier.width(16.dp))
                Button(onClick = onClose) {
                    Text("CANCEL")
                }
            }
        }
    }
}