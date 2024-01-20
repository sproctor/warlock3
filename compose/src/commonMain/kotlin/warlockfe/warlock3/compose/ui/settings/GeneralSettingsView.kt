package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.ScriptDirRepository
import warlockfe.warlock3.core.prefs.defaultMaxScrollLines
import warlockfe.warlock3.core.prefs.scrollbackKey

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
        ScrollableColumn {
            if (currentCharacter != null) {
                val initialMaxLines by characterSettingsRepository.observe(
                    characterId = currentCharacter.id, key = scrollbackKey
                ).collectAsState(null)
                var maxLinesValue by remember(initialMaxLines == null) {
                    mutableStateOf(
                        TextFieldValue(initialMaxLines ?: defaultMaxScrollLines.toString())
                    )
                }
                TextField(
                    value = maxLinesValue,
                    onValueChange = {
                        maxLinesValue = it
                        // TODO: use a view model here to handle scope
                        GlobalScope.launch {
                            characterSettingsRepository.save(
                                characterId = currentCharacter.id,
                                key = scrollbackKey,
                                value = it.text
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
            Column(
                Modifier.border(1.dp, Color.Black, RoundedCornerShape(8.dp)).padding(8.dp)
                    .fillMaxWidth()
            ) {
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
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete"
                                )
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
                var value by remember { mutableStateOf("") }
                AlertDialog(
                    title = { Text("Add a script directory") },
                    onDismissRequest = { showAddDirDialog = false },
                    confirmButton = {
                        val scope = rememberCoroutineScope()
                        TextButton(onClick = {
                            scope.launch {
                                scriptDirRepository.save(currentCharacterId, value)
                                showAddDirDialog = false
                            }
                        }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDirDialog = false }) {
                            Text("Cancel")
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text("Use %home% for the home directory and %config% for the Warlock config directory")
                            TextField(
                                value = value,
                                onValueChange = { value = it },
                                label = { Text("Directory path") })
                        }
                    }
                )
            }
        }
    }
}