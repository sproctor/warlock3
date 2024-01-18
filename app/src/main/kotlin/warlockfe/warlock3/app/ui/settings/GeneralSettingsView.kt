package warlockfe.warlock3.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.ScriptDirRepository
import warlockfe.warlock3.core.prefs.defaultMaxScrollLines
import warlockfe.warlock3.core.prefs.scrollbackKey
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun GeneralSettingsView(
    characterSettingsRepository: CharacterSettingsRepository,
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    scriptDirRepository: ScriptDirRepository,
) {
    val currentCharacterState =
        remember(initialCharacter, characters) { mutableStateOf(initialCharacter) }
    val currentCharacter = currentCharacterState.value
    val currentCharacterId = currentCharacter?.id ?: "global"

    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it },
            allowGlobal = true
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth()) {
            val scrollbarStyle = LocalScrollbarStyle.current
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(end = scrollbarStyle.thickness)
                    .verticalScroll(scrollState)
                    .fillMaxWidth(),
            ) {
                if (currentCharacter != null) {
                    val initialMaxLines by characterSettingsRepository.observe(
                        characterId = currentCharacter.id, key = scrollbackKey
                    ).collectAsState(null)
                    var maxLinesValue by remember(initialMaxLines == null) { mutableStateOf(TextFieldValue(initialMaxLines ?: defaultMaxScrollLines.toString())) }
                    TextField(
                        value = maxLinesValue,
                        onValueChange = {
                            maxLinesValue = it
                            // TODO: use a view model here to handle scope
                            GlobalScope.launch {
                                characterSettingsRepository.save(
                                    characterId = currentCharacter.id, key = scrollbackKey, value = it.text
                                )
                            }
                        },
                        label = {
                            Text("Maximum lines in scroll back buffer")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Text("Script directories", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                val scriptDirs by scriptDirRepository.observeScriptDirs(characterId = currentCharacterId)
                    .collectAsState(emptyList())
                Column(Modifier.border(1.dp, Color.Black, RoundedCornerShape(8.dp)).padding(8.dp).fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("%config%/scripts") }
                    )
                    scriptDirs.forEach { scriptDir ->
                        ListItem(
                            headlineContent = {
                                Text(scriptDir)
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        GlobalScope.launch {
                                            scriptDirRepository.delete(
                                                characterId = currentCharacterId,
                                                path = scriptDir
                                            )
                                        }
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                                }
                            },
                        )
                    }
                }
                var showAddDirDialog by remember { mutableStateOf(false) }
                Button(onClick = { showAddDirDialog = true }) {
                    Text("Add a directory")
                }
                if (showAddDirDialog) {
                    DialogWindow(
                        title = "Add a script directory",
                        onCloseRequest = { showAddDirDialog = false },
                        state = rememberDialogState()
                    ) {
                        Column(Modifier.padding(16.dp).fillMaxSize()) {
                            var value by remember { mutableStateOf("") }
                            Text("Use %home% for the home directory and %config% for the Warlock config directory")
                            TextField(value = value, onValueChange = { value = it }, label = { Text("Directory path") })
                            val scope = rememberCoroutineScope()
                            Button(onClick = {
                                scope.launch {
                                    scriptDirRepository.save(currentCharacterId, value)
                                    showAddDirDialog = false
                                }
                            }) {
                                Text("OK")
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState),
                // FIXME
//                style = scrollbarStyle.copy(
//                    hoverColor = MaterialTheme.colorScheme.primary,
//                    unhoverColor = MaterialTheme.colors.primary.copy(alpha = 0.42f)
//                )
            )
        }
    }
}