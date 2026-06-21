package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockCheckboxRow
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockRadioButtonRow
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.compose.util.createPlatformDialogSettings
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.ReleaseChannelSetting
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.DEFAULT_MAX_TYPE_AHEAD
import warlockfe.warlock3.core.prefs.repositories.MAX_TYPE_AHEAD_KEY
import warlockfe.warlock3.core.prefs.repositories.SCRIPT_COMMAND_PREFIX_KEY
import warlockfe.warlock3.core.prefs.repositories.ScriptDirRepository
import warlockfe.warlock3.core.util.LogType

@Composable
fun DesktopGeneralSettingsView(
    characterSettingsRepository: CharacterSettingsRepository,
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    scriptDirRepository: ScriptDirRepository,
    clientSettingRepository: ClientSettingRepository,
    modifier: Modifier = Modifier,
) {
    val currentCharacterState =
        remember(initialCharacter, characters) { mutableStateOf(initialCharacter) }
    val currentCharacter = currentCharacterState.value
    val currentCharacterId = currentCharacter?.id ?: "global"
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        DesktopSettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it },
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        WarlockScrollableColumn {
            val maxLinesValue = rememberTextFieldState()
            LaunchedEffect(Unit) {
                val initialMaxLines =
                    clientSettingRepository.observeMaxScrollLines().first().toString()
                maxLinesValue.setTextAndPlaceCursorAtEnd(initialMaxLines)
                snapshotFlow { maxLinesValue.text.toString() }
                    .collectLatest {
                        if (maxLinesValue.text.toString() != initialMaxLines && maxLinesValue.text.isNotBlank()) {
                            clientSettingRepository.putMaxScrollLines(
                                maxLinesValue.text.toString().toIntOrNull(),
                            )
                        }
                    }
            }
            Text("Maximum lines in scroll back buffer")
            WarlockTextField(state = maxLinesValue, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))

            val minCommandLengthValue = rememberTextFieldState()
            LaunchedEffect(Unit) {
                val initialMinCommandLength =
                    clientSettingRepository.observeMinCommandLength().first().toString()
                minCommandLengthValue.setTextAndPlaceCursorAtEnd(initialMinCommandLength)
                snapshotFlow { minCommandLengthValue.text.toString() }
                    .collectLatest {
                        if (it != initialMinCommandLength && it.isNotBlank()) {
                            clientSettingRepository.putMinCommandLength(it.toIntOrNull())
                        }
                    }
            }
            Text("Minimum command length for history")
            WarlockTextField(state = minCommandLengthValue, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))

            val markLinks by clientSettingRepository
                .observeMarkLinks()
                .collectAsState(initial = true)
            WarlockCheckboxRow(
                checked = markLinks,
                onCheckedChange = {
                    scope.launch {
                        clientSettingRepository.putMarkLinks(it)
                    }
                },
                text = "Mark links in text",
            )

            Spacer(Modifier.height(16.dp))

            val showImages by clientSettingRepository
                .observeShowImages()
                .collectAsState(initial = true)
            WarlockCheckboxRow(
                checked = showImages,
                onCheckedChange = {
                    scope.launch {
                        clientSettingRepository.putShowImages(it)
                    }
                },
                text = "Show images in stream",
            )

            Spacer(Modifier.height(16.dp))

            val suppressPrompts by clientSettingRepository
                .observeSuppressPrompts()
                .collectAsState(initial = false)
            WarlockCheckboxRow(
                checked = suppressPrompts,
                onCheckedChange = {
                    scope.launch {
                        clientSettingRepository.putSuppressPrompts(it)
                    }
                },
                text = "Hide prompts",
            )

            Spacer(Modifier.height(16.dp))

            val autoConnectLastConnection by clientSettingRepository
                .observeAutoConnectLastConnection()
                .collectAsState(initial = false)
            WarlockCheckboxRow(
                checked = autoConnectLastConnection,
                onCheckedChange = {
                    scope.launch {
                        clientSettingRepository.putAutoConnectLastConnection(it)
                    }
                },
                text = "Reconnect the last connection on startup",
            )

            Spacer(Modifier.height(16.dp))

            if (currentCharacterId != "global") {
                val maxTypeAheadState = rememberTextFieldState(DEFAULT_MAX_TYPE_AHEAD.toString())
                var maxTypeAheadError by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(maxTypeAheadState) {
                    val initialMaxTypeAhead =
                        characterSettingsRepository
                            .observe(
                                characterId = currentCharacterId,
                                key = MAX_TYPE_AHEAD_KEY,
                            ).first()
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
                                scope.launch {
                                    characterSettingsRepository.save(
                                        characterId = currentCharacterId,
                                        key = MAX_TYPE_AHEAD_KEY,
                                        value = it,
                                    )
                                }
                            }
                        }
                }
                Text("Maximum commands to type ahead. 0 to disable buffer")
                WarlockTextField(state = maxTypeAheadState, modifier = Modifier.fillMaxWidth())
                maxTypeAheadError?.let { Text(it) }

                Spacer(Modifier.height(16.dp))

                val scriptCommandPrefixState = rememberTextFieldState(".")
                LaunchedEffect(currentCharacterId) {
                    characterSettingsRepository
                        .get(currentCharacterId, SCRIPT_COMMAND_PREFIX_KEY)
                        ?.let { prefix ->
                            scriptCommandPrefixState.setTextAndPlaceCursorAtEnd(prefix)
                        }
                }
                Text("Script command prefix")
                WarlockTextField(state = scriptCommandPrefixState, modifier = Modifier.fillMaxWidth())
                LaunchedEffect(scriptCommandPrefixState.text) {
                    val initialScriptCommandPrefix =
                        characterSettingsRepository.get(currentCharacterId, SCRIPT_COMMAND_PREFIX_KEY)
                    if (scriptCommandPrefixState.text.toString() != initialScriptCommandPrefix) {
                        characterSettingsRepository.save(
                            characterId = currentCharacterId,
                            SCRIPT_COMMAND_PREFIX_KEY,
                            scriptCommandPrefixState.text.toString(),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Text("Script directories")
            Spacer(Modifier.height(8.dp))
            val scriptDirs by scriptDirRepository
                .observeScriptDirs(characterId = currentCharacterId)
                .collectAsState(emptyList())
            Column(
                Modifier
                    .border(
                        Dp.Hairline,
                        JewelTheme.globalColors.borders.normal,
                        RoundedCornerShape(6.dp),
                    ).padding(8.dp)
                    .fillMaxWidth(),
            ) {
                WarlockListItem(
                    headline = { Text(scriptDirRepository.getDefaultDir()) },
                )
                scriptDirs.forEach { scriptDir ->
                    WarlockListItem(
                        headline = { Text(scriptDir) },
                        trailing = {
                            WarlockOutlinedButton(
                                onClick = {
                                    scope.launch {
                                        scriptDirRepository.delete(
                                            characterId = currentCharacterId,
                                            path = scriptDir,
                                        )
                                    }
                                },
                                text = "Delete",
                            )
                        },
                    )
                }
            }

            val scriptDirLauncher =
                rememberDirectoryPickerLauncher(
                    dialogSettings = FileKitDialogSettings.createPlatformDialogSettings("Choose a script directory"),
                ) { directory ->
                    if (directory != null) {
                        scope.launch {
                            scriptDirRepository.save(currentCharacterId, directory.absolutePath())
                        }
                    }
                }
            WarlockButton(onClick = { scriptDirLauncher.launch() }, text = "Add a directory")

            Spacer(Modifier.height(16.dp))

            val currentTheme by clientSettingRepository.observeTheme().collectAsState(null)
            Text("Select theme")
            Column {
                ThemeSetting.entries.forEach { entry ->
                    WarlockRadioButtonRow(
                        selected = currentTheme == entry,
                        onClick = {
                            scope.launch { clientSettingRepository.putTheme(entry) }
                        },
                        text = entry.name,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val currentReleaseChannel by clientSettingRepository
                .observeReleaseChannel()
                .collectAsState(null)
            Text("Release channel to check for updates")
            Column {
                ReleaseChannelSetting.entries.forEach { entry ->
                    WarlockRadioButtonRow(
                        selected = currentReleaseChannel == entry,
                        onClick = {
                            scope.launch { clientSettingRepository.putReleaseChannel(entry) }
                        },
                        text = entry.name.lowercase(),
                    )
                }
            }
            Text("Changes take effect on next restart.")

            Spacer(Modifier.height(8.dp))

            val currentSkin by clientSettingRepository.observeSkinFile().collectAsState(null)
            Text("Select skin")
            val skinDisplay = rememberTextFieldState(currentSkin ?: "Default")
            LaunchedEffect(currentSkin) {
                skinDisplay.setTextAndPlaceCursorAtEnd(currentSkin ?: "Default")
            }
            WarlockTextField(state = skinDisplay, modifier = Modifier.fillMaxWidth(), readOnly = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WarlockOutlinedButton(
                    onClick = {
                        scope.launch { clientSettingRepository.putSkinFile(null) }
                    },
                    text = "Use default skin",
                    enabled = currentSkin != null,
                )
                val skinFileLauncher =
                    rememberFilePickerLauncher(
                        dialogSettings = FileKitDialogSettings.createPlatformDialogSettings("Choose a skin file"),
                        directory = currentSkin?.let { PlatformFile(it) },
                    ) { file ->
                        if (file != null) {
                            scope.launch {
                                clientSettingRepository.putSkinFile(file.absolutePath())
                            }
                        }
                    }
                WarlockButton(onClick = { skinFileLauncher.launch() }, text = "Change skin")
            }

            Spacer(Modifier.height(8.dp))

            val loggingSettings by clientSettingRepository.observeLogSettings().collectAsState(null)
            Text("Logging settings")
            if (loggingSettings != null) {
                val pathDisplay = rememberTextFieldState(loggingSettings!!.basePath)
                LaunchedEffect(loggingSettings!!.basePath) {
                    pathDisplay.setTextAndPlaceCursorAtEnd(loggingSettings!!.basePath)
                }
                WarlockTextField(state = pathDisplay, modifier = Modifier.fillMaxWidth(), readOnly = true)
                val loggingDirectoryLauncher =
                    rememberDirectoryPickerLauncher(
                        dialogSettings = FileKitDialogSettings.createPlatformDialogSettings("Choose a base logging directory"),
                        directory = PlatformFile(loggingSettings!!.basePath),
                    ) { directory ->
                        if (directory != null) {
                            scope.launch {
                                clientSettingRepository.putLoggingPath(directory.absolutePath())
                            }
                        }
                    }
                WarlockButton(
                    onClick = { loggingDirectoryLauncher.launch() },
                    text = "Change directory",
                )

                Text("Select logging style")
                Column {
                    LogType.entries.forEach { entry ->
                        WarlockRadioButtonRow(
                            selected = loggingSettings!!.type == entry,
                            onClick = {
                                scope.launch { clientSettingRepository.putLoggingType(entry) }
                            },
                            text = entry.name,
                        )
                    }
                }

                WarlockCheckboxRow(
                    checked = loggingSettings!!.logTimestamps,
                    onCheckedChange = {
                        scope.launch { clientSettingRepository.putLoggingTimestamps(it) }
                    },
                    text = "Add timestamps to logs",
                )
            }
            Spacer(Modifier.width(0.dp))
        }
    }
}
