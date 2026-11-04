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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.compose.ui.settings.GeneralSettingsViewModel
import warlockfe.warlock3.compose.util.createPlatformDialogSettings
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.ReleaseChannelSetting
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.repositories.DEFAULT_MAX_TYPE_AHEAD
import warlockfe.warlock3.core.util.LogType

@Composable
fun DesktopGeneralSettingsView(
    viewModel: GeneralSettingsViewModel,
    characters: List<GameCharacter>,
    modifier: Modifier = Modifier,
) {
    val currentCharacter by viewModel.currentCharacter.collectAsState()
    val currentCharacterId = currentCharacter?.id ?: GLOBAL_CHARACTER_ID

    Column(modifier.fillMaxSize()) {
        DesktopSettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = viewModel::selectCharacter,
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        WarlockScrollableColumn {
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
            Text("Maximum lines in scroll back buffer")
            WarlockTextField(state = maxLinesValue, modifier = Modifier.fillMaxWidth())

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
            Text("Minimum command length for history")
            WarlockTextField(state = minCommandLengthValue, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))

            val markLinks by viewModel.markLinks.collectAsState()
            CheckboxRow(
                checked = markLinks,
                onCheckedChange = viewModel::putMarkLinks,
                text = "Mark links in text",
            )

            Spacer(Modifier.height(16.dp))

            val showImages by viewModel.showImages.collectAsState()
            CheckboxRow(
                checked = showImages,
                onCheckedChange = viewModel::putShowImages,
                text = "Show images in stream",
            )

            Spacer(Modifier.height(16.dp))

            val suppressPrompts by viewModel.suppressPrompts.collectAsState()
            CheckboxRow(
                checked = suppressPrompts,
                onCheckedChange = viewModel::putSuppressPrompts,
                text = "Hide prompts",
            )

            Spacer(Modifier.height(16.dp))

            val autoConnectLastConnection by viewModel.autoConnectLastConnection.collectAsState()
            CheckboxRow(
                checked = autoConnectLastConnection,
                onCheckedChange = viewModel::putAutoConnectLastConnection,
                text = "Reconnect the last connection on startup",
            )

            Spacer(Modifier.height(16.dp))

            if (currentCharacterId != GLOBAL_CHARACTER_ID) {
                // Keyed on the character: the fields' text and the effects that load and save it
                // must start over for each one, or switching straight from one character to
                // another shows, and saves to, the first.
                key(currentCharacterId) {
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
                    Text("Maximum commands to type ahead. 0 to disable buffer")
                    WarlockTextField(state = maxTypeAheadState, modifier = Modifier.fillMaxWidth())
                    maxTypeAheadError?.let { Text(it) }

                    Spacer(Modifier.height(16.dp))

                    val scriptCommandPrefixState = rememberTextFieldState(".")
                    LaunchedEffect(currentCharacterId) {
                        viewModel
                            .getScriptCommandPrefix(currentCharacterId)
                            ?.let { prefix ->
                                scriptCommandPrefixState.setTextAndPlaceCursorAtEnd(prefix)
                            }
                    }
                    Text("Script command prefix")
                    WarlockTextField(state = scriptCommandPrefixState, modifier = Modifier.fillMaxWidth())
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
            }

            Text("Script directories")
            Spacer(Modifier.height(8.dp))
            val scriptDirs by viewModel.scriptDirs.collectAsState()
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
                    headline = { Text(viewModel.defaultScriptDir) },
                )
                scriptDirs.forEach { scriptDir ->
                    WarlockListItem(
                        headline = { Text(scriptDir) },
                        trailing = {
                            WarlockOutlinedButton(
                                onClick = { viewModel.deleteScriptDir(scriptDir) },
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
                        viewModel.addScriptDir(directory.absolutePath())
                    }
                }
            WarlockButton(onClick = { scriptDirLauncher.launch() }, text = "Add a directory")

            Spacer(Modifier.height(16.dp))

            val currentTheme by viewModel.theme.collectAsState()
            Text("Select theme")
            Column {
                ThemeSetting.entries.forEach { entry ->
                    RadioButtonRow(
                        selected = currentTheme == entry,
                        onClick = { viewModel.putTheme(entry) },
                        text = entry.name,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val currentReleaseChannel by viewModel.releaseChannel.collectAsState()
            Text("Release channel to check for updates")
            Column {
                ReleaseChannelSetting.entries.forEach { entry ->
                    RadioButtonRow(
                        selected = currentReleaseChannel == entry,
                        onClick = { viewModel.putReleaseChannel(entry) },
                        text = entry.label(),
                    )
                }
            }
            Text("Changes take effect on next restart.")

            Spacer(Modifier.height(8.dp))

            val currentSkin by viewModel.skinFile.collectAsState()
            Text("Select skin")
            val skinDisplay = rememberTextFieldState(currentSkin ?: "Default")
            LaunchedEffect(currentSkin) {
                skinDisplay.setTextAndPlaceCursorAtEnd(currentSkin ?: "Default")
            }
            WarlockTextField(state = skinDisplay, modifier = Modifier.fillMaxWidth(), readOnly = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WarlockOutlinedButton(
                    onClick = { viewModel.putSkinFile(null) },
                    text = "Use default skin",
                    enabled = currentSkin != null,
                )
                val skinFileLauncher =
                    rememberFilePickerLauncher(
                        dialogSettings = FileKitDialogSettings.createPlatformDialogSettings("Choose a skin file"),
                        directory = currentSkin?.let { PlatformFile(it) },
                    ) { file ->
                        if (file != null) {
                            viewModel.putSkinFile(file.absolutePath())
                        }
                    }
                WarlockButton(onClick = { skinFileLauncher.launch() }, text = "Change skin")
            }

            Spacer(Modifier.height(8.dp))

            val loggingSettings by viewModel.logSettings.collectAsState()
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
                            viewModel.putLoggingPath(directory.absolutePath())
                        }
                    }
                WarlockButton(
                    onClick = { loggingDirectoryLauncher.launch() },
                    text = "Change directory",
                )

                Text("Select logging style")
                Column {
                    LogType.entries.forEach { entry ->
                        RadioButtonRow(
                            selected = loggingSettings!!.type == entry,
                            onClick = { viewModel.putLoggingType(entry) },
                            text = entry.name,
                        )
                    }
                }

                CheckboxRow(
                    checked = loggingSettings!!.logTimestamps,
                    onCheckedChange = viewModel::putLoggingTimestamps,
                    text = "Add timestamps to logs",
                )
            }
            Spacer(Modifier.width(0.dp))
        }
    }
}

/**
 * Each channel also picks up everything more stable than itself, so a beta build still moves to the
 * stable release that supersedes it. The labels spell that out - "beta" alone reads like it would
 * strand you on prereleases.
 */
private fun ReleaseChannelSetting.label(): String =
    when (this) {
        ReleaseChannelSetting.CURRENT -> "Current - stay on the channel this build came from"
        ReleaseChannelSetting.STABLE -> "Stable - stable releases only"
        ReleaseChannelSetting.BETA -> "Beta - betas and stable releases"
        ReleaseChannelSetting.ALPHA -> "Alpha - the newest release of any kind"
    }
