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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.error_filled
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.ScriptDirRepository
import warlockfe.warlock3.core.prefs.repositories.defaultMaxTypeAhead
import warlockfe.warlock3.core.prefs.repositories.maxTypeAheadKey
import warlockfe.warlock3.core.prefs.repositories.scriptCommandPrefixKey
import warlockfe.warlock3.core.util.LogType
import warlockfe.warlock3.wrayth.settings.WraythImporter
import java.io.File

@Composable
fun GeneralSettingsView(
    characterSettingsRepository: CharacterSettingsRepository,
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    scriptDirRepository: ScriptDirRepository,
    clientSettingRepository: ClientSettingRepository,
    wraythImporter: WraythImporter,
) {
    val currentCharacterState =
        remember(initialCharacter, characters) { mutableStateOf(initialCharacter) }
    val currentCharacter = currentCharacterState.value
    val currentCharacterId = currentCharacter?.id ?: "global"
    val scope = rememberCoroutineScope()
    var showImportDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it },
            allowGlobal = true
        )
        Spacer(Modifier.height(16.dp))
        val wraythSettingsFileLauncher = rememberFilePickerLauncher(
            title = "Choose Wrayth settings file to import",
        ) { platformFile ->
            if (platformFile != null) {
                scope.launch(NonCancellable) {
                    if (!wraythImporter.importFile(currentCharacterId, File(platformFile.absolutePath()))) {
                        showImportDialog = true
                    }
                }
            }
        }
        Button(
            onClick = {
                wraythSettingsFileLauncher.launch()
            }
        ) {
            Text("Import settings from Wrayth settings file")
        }
        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                confirmButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("OK")
                    }
                },
                text = { Text("Import settings failed") }
            )
        }
        Spacer(Modifier.height(16.dp))
        ScrollableColumn {
            val maxLinesValue = rememberTextFieldState()
            LaunchedEffect(maxLinesValue) {
                val initialMaxLines = clientSettingRepository.observeMaxScrollLines().first().toString()
                maxLinesValue.setTextAndPlaceCursorAtEnd(initialMaxLines)
                snapshotFlow { maxLinesValue.text.toString() }
                    .collectLatest {
                        if (maxLinesValue.text.toString() != initialMaxLines) {
                            clientSettingRepository.putMaxScrollLines(maxLinesValue.text.toString().toIntOrNull())
                        }
                    }
            }
            TextField(
                state = maxLinesValue,
                label = {
                    Text("Maximum lines in scroll back buffer")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            Spacer(Modifier.height(16.dp))

            Row {
                val markLinks by clientSettingRepository.observeMarkLinks().collectAsState(initial = true)
                Switch(
                    checked = markLinks,
                    onCheckedChange = {
                        // TODO: this change should happen synchronously
                        scope.launch {
                            clientSettingRepository.putMarkLinks(it)
                        }
                    }
                )
                Spacer(Modifier.width(16.dp))
                Text("Mark links in text")
            }

            Spacer(Modifier.height(16.dp))

            if (currentCharacterId != "global") {
                val maxTypeAheadState = rememberTextFieldState(defaultMaxTypeAhead.toString())
                var maxTypeAheadError by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(maxTypeAheadState) {
                    val initialMaxTypeAhead =
                        characterSettingsRepository.observe(characterId = currentCharacterId, key = maxTypeAheadKey)
                            .first()
                    if (initialMaxTypeAhead != null) {
                        maxTypeAheadState.setTextAndPlaceCursorAtEnd(initialMaxTypeAhead)
                    }
                    snapshotFlow { maxTypeAheadState.text.toString() }
                        .collectLatest {
                            val value = it.toIntOrNull()
                            if (value == null) {
                                maxTypeAheadError = "Invalid number"
                            } else if (value < 0) {
                                maxTypeAheadError = "Must be non-negative"
                            } else {
                                maxTypeAheadError = null
                                scope.launch(NonCancellable) {
                                    characterSettingsRepository.save(
                                        characterId = currentCharacterId,
                                        key = maxTypeAheadKey,
                                        value = it
                                    )
                                }
                            }
                        }
                }
                TextField(
                    state = maxTypeAheadState,
                    label = {
                        Text("Maximum commands to type ahead. 0 to disable buffer")
                    },
                    supportingText = {
                        if (maxTypeAheadError != null) {
                            Text(maxTypeAheadError!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    isError = maxTypeAheadError != null,
                    trailingIcon = {
                        if (maxTypeAheadError != null) {
                            Icon(painterResource(Res.drawable.error_filled), contentDescription = null)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )

                Spacer(Modifier.height(16.dp))

                val scriptCommandPrefixState = rememberTextFieldState(".")
                LaunchedEffect(currentCharacterId) {
                    characterSettingsRepository.get(currentCharacterId, scriptCommandPrefixKey)?.let { prefix ->
                        scriptCommandPrefixState.setTextAndPlaceCursorAtEnd(prefix)
                    }
                }
                TextField(
                    state = scriptCommandPrefixState,
                    label = {
                        Text("Script command prefix")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                LaunchedEffect(scriptCommandPrefixState.text) {
                    val initialScriptCommandPrefix = characterSettingsRepository.get(currentCharacterId, scriptCommandPrefixKey)
                    if (scriptCommandPrefixState.text.toString() != initialScriptCommandPrefix) {
                        characterSettingsRepository.save(
                            characterId = currentCharacterId,
                            scriptCommandPrefixKey,
                            scriptCommandPrefixState.text.toString()
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

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
                                    painter = painterResource(Res.drawable.delete),
                                    contentDescription = "Delete"
                                )
                            }
                        },
                    )
                }
            }

            val scriptDirLauncher = rememberDirectoryPickerLauncher(
                title = "Choose a script directory",
            ) { directory ->
                if (directory != null) {
                    scope.launch {
                        scriptDirRepository.save(currentCharacterId, directory.absolutePath())
                    }
                }
            }
            Button(
                onClick = {
                    scriptDirLauncher.launch()
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
            val skinFileLauncher = rememberFilePickerLauncher(
                title = "Choose a skin file",
                directory = currentSkin
                    ?.takeIf { File(it).exists() }
                    ?.let { PlatformFile(it) }
            ) { file ->
                if (file != null) {
                    scope.launch {
                        clientSettingRepository.putSkinFile(file.absolutePath())
                    }
                }
            }
            Button(
                onClick = {
                    skinFileLauncher.launch()
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
                val loggingDirectoryLauncher = rememberDirectoryPickerLauncher(
                    title = "Choose a base logging directory",
                    directory = PlatformFile(loggingSettings!!.basePath)
                ) { directory ->
                    if (directory != null) {
                        scope.launch {
                            clientSettingRepository.putLoggingPath(directory.absolutePath())
                        }
                    }
                }
                Button(
                    onClick = {
                        loggingDirectoryLauncher.launch()
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