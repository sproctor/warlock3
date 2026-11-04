package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.RadioRow
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.components.SwitchRow
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.error_filled
import warlockfe.warlock3.compose.util.createPlatformDialogSettings
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.repositories.DEFAULT_MAX_TYPE_AHEAD
import warlockfe.warlock3.core.util.LogType

@Composable
fun GeneralSettingsView(
    viewModel: GeneralSettingsViewModel,
    characters: List<GameCharacter>,
    modifier: Modifier = Modifier,
) {
    val currentCharacter by viewModel.currentCharacter.collectAsState()
    val currentCharacterId = currentCharacter?.id ?: GLOBAL_CHARACTER_ID
    var importResultMessages by remember { mutableStateOf(emptyList<String>()) }

    Column(modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = viewModel::selectCharacter,
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        val wraythSettingsFileLauncher =
            rememberFilePickerLauncher(
                dialogSettings = FileKitDialogSettings.createPlatformDialogSettings("Choose Wrayth settings file to import"),
            ) { platformFile ->
                if (platformFile != null) {
                    viewModel.importWraythFile(platformFile.absolutePath()) { messages ->
                        importResultMessages = messages
                    }
                }
            }
        Button(
            onClick = {
                wraythSettingsFileLauncher.launch()
            },
        ) {
            Text("Import settings from Wrayth settings file")
        }
        if (importResultMessages.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { importResultMessages = emptyList() },
                confirmButton = {
                    TextButton(onClick = { importResultMessages = emptyList() }) {
                        Text("OK")
                    }
                },
                text = {
                    SelectionContainer {
                        ScrollableColumn {
                            importResultMessages.forEach { message ->
                                Text(message)
                            }
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        ScrollableColumn {
            val maxLinesValue = rememberTextFieldState()
            LaunchedEffect(Unit) {
                val initialMaxLines =
                    viewModel.getMaxScrollLines().toString()
                maxLinesValue.setTextAndPlaceCursorAtEnd(initialMaxLines)
                snapshotFlow { maxLinesValue.text.toString() }
                    .collectLatest {
                        if (maxLinesValue.text.toString() != initialMaxLines && maxLinesValue.text.isNotBlank()) {
                            viewModel.putMaxScrollLines(
                                maxLinesValue.text.toString().toIntOrNull(),
                            )
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

            val minCommandLengthValue = rememberTextFieldState()
            LaunchedEffect(Unit) {
                val initialMinCommandLength =
                    viewModel.getMinCommandLength().toString()
                minCommandLengthValue.setTextAndPlaceCursorAtEnd(initialMinCommandLength)
                snapshotFlow { minCommandLengthValue.text.toString() }
                    .collectLatest {
                        if (it != initialMinCommandLength && it.isNotBlank()) {
                            viewModel.putMinCommandLength(it.toIntOrNull())
                        }
                    }
            }
            TextField(
                state = minCommandLengthValue,
                label = {
                    Text("Minimum command length for history")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            Spacer(Modifier.height(16.dp))

            val markLinks by viewModel.markLinks.collectAsState()
            SwitchRow(
                checked = markLinks,
                onCheckedChange = viewModel::putMarkLinks,
                text = "Mark links in text",
            )

            Spacer(Modifier.height(16.dp))

            val showImages by viewModel.showImages.collectAsState()
            SwitchRow(
                checked = showImages,
                onCheckedChange = viewModel::putShowImages,
                text = "Show images in stream",
            )

            Spacer(Modifier.height(16.dp))

            val suppressPrompts by viewModel.suppressPrompts.collectAsState()
            SwitchRow(
                checked = suppressPrompts,
                onCheckedChange = viewModel::putSuppressPrompts,
                text = "Hide prompts",
            )

            Spacer(Modifier.height(16.dp))

            val autoConnectLastConnection by viewModel.autoConnectLastConnection.collectAsState()
            SwitchRow(
                checked = autoConnectLastConnection,
                onCheckedChange = viewModel::putAutoConnectLastConnection,
                text = "Reconnect the last connection on startup",
            )

            Spacer(Modifier.height(16.dp))

            if (currentCharacterId != GLOBAL_CHARACTER_ID) {
                val maxTypeAheadState = rememberTextFieldState(DEFAULT_MAX_TYPE_AHEAD.toString())
                var maxTypeAheadError by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(maxTypeAheadState) {
                    val initialMaxTypeAhead = viewModel.getMaxTypeAhead(currentCharacterId)
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
                                viewModel.saveMaxTypeAhead(currentCharacterId, it)
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
                            Icon(
                                painterResource(Res.drawable.error_filled),
                                contentDescription = null,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )

                Spacer(Modifier.height(16.dp))

                val scriptCommandPrefixState = rememberTextFieldState(".")
                LaunchedEffect(currentCharacterId) {
                    viewModel
                        .getScriptCommandPrefix(currentCharacterId)
                        ?.let { prefix ->
                            scriptCommandPrefixState.setTextAndPlaceCursorAtEnd(prefix)
                        }
                }
                TextField(
                    state = scriptCommandPrefixState,
                    label = {
                        Text("Script command prefix")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                LaunchedEffect(scriptCommandPrefixState.text) {
                    val initialScriptCommandPrefix = viewModel.getScriptCommandPrefix(currentCharacterId)
                    if (scriptCommandPrefixState.text.toString() != initialScriptCommandPrefix) {
                        viewModel.saveScriptCommandPrefix(
                            characterId = currentCharacterId,
                            scriptCommandPrefixState.text.toString(),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Text("Script directories", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            val scriptDirs by viewModel.scriptDirs.collectAsState()
            Column(
                Modifier
                    .border(
                        Dp.Hairline,
                        MaterialTheme.colorScheme.outline,
                        MaterialTheme.shapes.medium,
                    ).padding(8.dp)
                    .fillMaxWidth(),
            ) {
                ListItem(
                    headlineContent = { Text(viewModel.defaultScriptDir) },
                )
                scriptDirs.forEach { scriptDir ->
                    ListItem(
                        headlineContent = {
                            Text(scriptDir)
                        },
                        trailingContent = {
                            IconButton(
                                onClick = { viewModel.deleteScriptDir(scriptDir) },
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.delete),
                                    contentDescription = "Delete",
                                )
                            }
                        },
                    )
                }
            }

            val scriptDirLauncher =
                rememberDirectoryPickerLauncher(
                    dialogSettings = FileKitDialogSettings.createPlatformDialogSettings("Choose a script directory"),
                ) { directory ->
                    if (directory != null) {
                        viewModel.addScriptDir(directory.absolutePath())
                    }
                }
            Button(
                onClick = {
                    scriptDirLauncher.launch()
                },
            ) {
                Text("Add a directory")
            }

            Spacer(Modifier.height(16.dp))

            val currentTheme by viewModel.theme.collectAsState()
            Text("Select theme")
            Column(Modifier.selectableGroup()) {
                ThemeSetting.entries.forEach { entry ->
                    RadioRow(
                        selected = currentTheme == entry,
                        onClick = { viewModel.putTheme(entry) },
                        text = entry.name,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val currentSkin by viewModel.skinFile.collectAsState()
            Text("Select skin")
            OutlinedTextField(
                value = currentSkin ?: "Default",
                readOnly = true,
                onValueChange = {},
                singleLine = true,
            )
            Button(
                onClick = { viewModel.putSkinFile(null) },
                enabled = currentSkin != null,
            ) {
                Text("Use default skin")
            }
            val skinFileLauncher =
                rememberFilePickerLauncher(
                    dialogSettings = FileKitDialogSettings.createPlatformDialogSettings("Choose a skin file"),
                    directory =
                        currentSkin
                            // ?.takeIf { File(it).exists() }
                            ?.let { PlatformFile(it) },
                ) { file ->
                    if (file != null) {
                        viewModel.putSkinFile(file.absolutePath())
                    }
                }
            Button(
                onClick = {
                    skinFileLauncher.launch()
                },
            ) {
                Text("Change skin")
            }

            Spacer(Modifier.height(8.dp))

            val loggingSettings by viewModel.logSettings.collectAsState()
            Text("Logging settings")
            if (loggingSettings != null) {
                OutlinedTextField(
                    value = loggingSettings!!.basePath,
                    readOnly = true,
                    onValueChange = {},
                    singleLine = true,
                )
                val loggingDirectoryLauncher =
                    rememberDirectoryPickerLauncher(
                        dialogSettings = FileKitDialogSettings.createPlatformDialogSettings("Choose a base logging directory"),
                        directory = PlatformFile(loggingSettings!!.basePath),
                    ) { directory ->
                        if (directory != null) {
                            viewModel.putLoggingPath(directory.absolutePath())
                        }
                    }
                Button(
                    onClick = {
                        loggingDirectoryLauncher.launch()
                    },
                ) {
                    Text("Change directory")
                }

                Text("Select logging style")
                Column(Modifier.selectableGroup()) {
                    LogType.entries.forEach { entry ->
                        RadioRow(
                            selected = loggingSettings!!.type == entry,
                            onClick = { viewModel.putLoggingType(entry) },
                            text = entry.name,
                        )
                    }
                }

                SwitchRow(
                    checked = loggingSettings!!.logTimestamps,
                    onCheckedChange = viewModel::putLoggingTimestamps,
                    text = "Add timestamps to logs",
                )
            }
        }
    }
}
