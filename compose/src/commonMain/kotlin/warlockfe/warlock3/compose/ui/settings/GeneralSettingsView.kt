package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.ClientSettingRepository
import warlockfe.warlock3.core.prefs.ScriptDirRepository
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.prefs.defaultMaxTypeAhead
import warlockfe.warlock3.core.prefs.maxTypeAheadKey
import warlockfe.warlock3.core.util.LogType
import java.io.File

@Composable
fun GeneralSettingsView(
    characterSettingsRepository: CharacterSettingsRepository,
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    scriptDirRepository: ScriptDirRepository,
    clientSettingRepository: ClientSettingRepository,
) {
    val currentCharacterState =
        remember(initialCharacter, characters) { mutableStateOf(initialCharacter) }
    val currentCharacter = currentCharacterState.value
    val currentCharacterId = currentCharacter?.id ?: "global"
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it },
            allowGlobal = true
        )
        Spacer(Modifier.height(16.dp))
        ScrollableColumn {
            val initialMaxLines by clientSettingRepository.observeMaxScrollLines().collectAsState(null)
            var maxLinesValue by remember(initialMaxLines == null) {
                mutableStateOf(
                    TextFieldValue(initialMaxLines?.toString() ?: "")
                )
            }
            TextField(
                value = maxLinesValue,
                onValueChange = {
                    maxLinesValue = it
                    scope.launch(NonCancellable) {
                        clientSettingRepository.putMaxScrollLines(it.text.toIntOrNull())
                    }
                },
                label = {
                    Text("Maximum lines in scroll back buffer")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Spacer(Modifier.height(16.dp))

            val initialMaxTypeAhead by characterSettingsRepository.observe(
                characterId = "global", key = maxTypeAheadKey
            ).collectAsState(null)
            var maxTypeAheadValue by remember(initialMaxTypeAhead == null) {
                mutableStateOf(
                    TextFieldValue(initialMaxTypeAhead ?: defaultMaxTypeAhead.toString())
                )
            }
            TextField(
                value = maxTypeAheadValue,
                onValueChange = {
                    maxTypeAheadValue = it
                    scope.launch(NonCancellable) {
                        characterSettingsRepository.save(
                            characterId = "global",
                            key = maxTypeAheadKey,
                            value = it.text
                        )
                    }
                },
                label = {
                    Text("Maximum commands to type ahead. 0 to disable buffer")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Spacer(Modifier.height(16.dp))

            Text("Script directories", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            val scriptDirs by scriptDirRepository.observeScriptDirs(characterId = currentCharacterId)
                .collectAsState(emptyList())
            Column(
                Modifier.border(Dp.Hairline, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text(scriptDirRepository.getDefaultDir()) }
                )
                scriptDirs.forEach { scriptDir ->
                    ListItem(
                        headlineContent = {
                            Text(scriptDir)
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    scope.launch(NonCancellable) {
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

            Button(
                onClick = {
                    scope.launch {
                        val directory = FileKit.openDirectoryPicker(
                            title = "Choose a script directory",
                        )
                        if (directory != null) {
                            scriptDirRepository.save(currentCharacterId, directory.absolutePath())
                        }
                    }
                }
            ) {
                Text("Add a directory")
            }

            Spacer(Modifier.height(16.dp))

            val currentTheme by clientSettingRepository.observeTheme().collectAsState(null)
            Text("Select theme")
            ThemeSetting.entries.forEach { entry ->
                Row(
                    Modifier.clickable {
                        scope.launch {
                            clientSettingRepository.putTheme(entry)
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = currentTheme == entry,
                        onClick = {
                            scope.launch(NonCancellable) {
                                clientSettingRepository.putTheme(entry)
                            }
                        }
                    )
                    Text(entry.name)
                }
            }

            Spacer(Modifier.height(8.dp))

            val currentSkin by clientSettingRepository.observeSkinFile().collectAsState(null)
            Text("Select skin")
            OutlinedTextField(
                value = currentSkin ?: "Default",
                readOnly = true,
                onValueChange = {},
                singleLine = true,
            )
            Button(
                onClick = {
                    scope.launch {
                        clientSettingRepository.putSkinFile(null)
                    }
                },
                enabled = currentSkin != null,
            ) {
                Text("Use default skin")
            }
            Button(
                onClick = {
                    scope.launch {
                        val file = FileKit.openFilePicker(
                            title = "Choose a skin file",
                            directory = currentSkin
                                ?.takeIf { File(it).exists() }
                                ?.let { PlatformFile(it) },
                        )
                        if (file != null) {
                            clientSettingRepository.putSkinFile(file.absolutePath())
                        }
                    }
                }
            ) {
                Text("Change skin")
            }

            Spacer(Modifier.height(8.dp))

            val loggingSettings by clientSettingRepository.observeLogSettings().collectAsState(null)
            Text("Logging settings")
            if (loggingSettings != null) {
                OutlinedTextField(
                    value = loggingSettings!!.basePath,
                    readOnly = true,
                    onValueChange = {},
                    singleLine = true,
                )
                Button(
                    onClick = {
                        scope.launch {
                            val directory = FileKit.openDirectoryPicker(
                                title = "Choose a base logging directory",
                                directory = PlatformFile(loggingSettings!!.basePath)
                            )
                            if (directory != null) {
                                clientSettingRepository.putLoggingPath(directory.absolutePath())
                            }
                        }
                    }
                ) {
                    Text("Change directory")
                }

                Text("Select logging style")
                LogType.entries.forEach { entry ->
                    Row(
                        modifier = Modifier.clickable {
                            scope.launch(NonCancellable) {
                                clientSettingRepository.putLoggingType(entry)
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = loggingSettings!!.type == entry,
                            onClick = {
                                scope.launch(NonCancellable) {
                                    clientSettingRepository.putLoggingType(entry)
                                }
                            }
                        )
                        Text(entry.name)
                    }
                }

                Row(
                    modifier = Modifier.clickable {
                        scope.launch(NonCancellable) {
                            clientSettingRepository.putLoggingTimestamps(!loggingSettings!!.logTimestamps)
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = loggingSettings!!.logTimestamps,
                        onCheckedChange = {
                            scope.launch(NonCancellable) {
                                clientSettingRepository.putLoggingTimestamps(!loggingSettings!!.logTimestamps)
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Add timestamps to logs")
                }
            }
        }
    }
}