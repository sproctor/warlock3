package cc.warlock.warlock3.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import cc.warlock.warlock3.core.client.GameCharacter
import cc.warlock.warlock3.core.prefs.CharacterSettingsRepository
import cc.warlock.warlock3.core.prefs.ScriptDirRepository
import cc.warlock.warlock3.core.prefs.defaultMaxScrollLines
import cc.warlock.warlock3.core.prefs.scrollbackKey
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
                    val maxLines by characterSettingsRepository.observe(
                        characterId = currentCharacter.id, key = scrollbackKey
                    ).collectAsState(null)
                    TextField(
                        value = maxLines ?: defaultMaxScrollLines.toString(),
                        onValueChange = {
                            GlobalScope.launch {
                                characterSettingsRepository.save(
                                    characterId = currentCharacter.id, key = scrollbackKey, value = it
                                )
                            }
                        },
                        label = {
                            Text("Maximum lines in scroll back buffer")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Text("Script directories", style = MaterialTheme.typography.h5)
                Text("\$HOME/.warlock3/scripts is always added to this list")
                Spacer(Modifier.height(8.dp))
                val scriptDirs by scriptDirRepository.observeScriptDirs(characterId = currentCharacter?.id ?: "global")
                    .collectAsState(emptyList())
                Column(Modifier.border(1.dp, Color.Black, RoundedCornerShape(8.dp)).padding(8.dp)) {
                    scriptDirs.forEach { scriptDir ->
                        Text(scriptDir)
                    }
                }
                var showAddDirDialog by remember { mutableStateOf(false) }
                Button(onClick = { showAddDirDialog = true }) {
                    Text("Add a directory")
                }
                if (showAddDirDialog) {
                    Dialog(
                        title = "Add a script directory",
                        onCloseRequest = { showAddDirDialog = false },
                        state = rememberDialogState()
                    ) {
                        Column {
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
                style = scrollbarStyle.copy(
                    hoverColor = MaterialTheme.colors.primary,
                    unhoverColor = MaterialTheme.colors.primary.copy(alpha = 0.42f)
                )
            )
        }
    }
}