package warlockfe.warlock3.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import io.github.kdroidfilter.nucleus.core.runtime.Platform
import io.github.kdroidfilter.nucleus.window.DecoratedWindowScope
import io.github.kdroidfilter.nucleus.window.material.MaterialTitleBar
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.utils.toKotlinxIoPath
import kotlinx.io.files.Path
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.space_dashboard_filled
import warlockfe.warlock3.compose.generated.resources.space_dashboard_outlined
import warlockfe.warlock3.compose.util.createPlatformDialogSettings
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DecoratedWindowScope.TitleBarView(
    title: String,
    sideBarVisible: Boolean,
    showSideBar: (Boolean) -> Unit,
    isConnected: Boolean,
    openNewWindow: () -> Unit,
    showSettingsDialog: () -> Unit,
    disconnect: () -> Unit,
    scriptDirectory: String?,
    runScript: (Path) -> Unit,
    showUpdateDialog: () -> Unit,
    showAboutDialog: () -> Unit,
    exportSettings: (File) -> Unit,
) {
    val scriptFilePickerLauncher = rememberFilePickerLauncher(
        dialogSettings = FileKitDialogSettings.createPlatformDialogSettings("Run script"),
        directory = scriptDirectory?.let { PlatformFile(it) },
    ) { file ->
        if (file != null) {
            runScript(file.file.toKotlinxIoPath())
        }
    }
    val exportFileSaveLauncher = rememberFileSaverLauncher { file ->
        if (file != null) {
            exportSettings(file.file)
        }
    }
    MaterialTitleBar {
        Row(Modifier.align(Alignment.Start)) {
            if (isConnected) {
                IconButton(onClick = { showSideBar(!sideBarVisible) }) {
                    Icon(
                        painter = painterResource(
                            if (sideBarVisible) {
                                Res.drawable.space_dashboard_filled
                            } else {
                                Res.drawable.space_dashboard_outlined
                            }
                        ),
                        tint = LocalContentColor.current,
                        contentDescription = null,
                    )
                }
            }
            if (Platform.Current == Platform.MacOS) {
                AppMenuBar(
                    isConnected = isConnected,
                    openNewWindow = openNewWindow,
                    showSettingsDialog = showSettingsDialog,
                    disconnect = disconnect,
                    runScript = scriptFilePickerLauncher::launch,
                    showUpdateDialog = showUpdateDialog,
                    showAboutDialog = showAboutDialog,
                    exportSettings = {
                        exportFileSaveLauncher.launch("settings", "json")
                    },
                )
            } else {
                var active by remember { mutableStateOf(false) }
                var currentMenu by remember { mutableStateOf<Menus?>(null) }
                Box {
                    TextButton(
                        modifier = Modifier.onPointerEvent(PointerEventType.Enter) {
                            currentMenu = Menus.FILE
                        },
                        onClick = {
                            active = !active
                            currentMenu = Menus.FILE
                        },
                    ) {
                        Text("File")
                    }
                    DropdownMenu(
                        expanded = active && currentMenu == Menus.FILE,
                        onDismissRequest = { active = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("New window") },
                            onClick = openNewWindow,
                        )
                        DropdownMenuItem(
                            text = { Text("Run script...") },
                            enabled = isConnected,
                            onClick = scriptFilePickerLauncher::launch,
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Export settings...") },
                            onClick = {
                                exportFileSaveLauncher.launch("settings", "json")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Settings...") },
                            onClick = showSettingsDialog,
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Disconnect") },
                            enabled = isConnected,
                            onClick = disconnect,
                        )
                    }
                }
                Box {
                    TextButton(
                        modifier = Modifier.onPointerEvent(PointerEventType.Enter) {
                            currentMenu = Menus.HELP
                        },
                        onClick = {
                            active = !active
                            currentMenu = Menus.HELP
                        },
                    ) {
                        Text("Help")
                    }
                    DropdownMenu(
                        expanded = active && currentMenu == Menus.HELP,
                        onDismissRequest = { active = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Updates") },
                            onClick = showUpdateDialog,
                        )
                        DropdownMenuItem(
                            text = { Text("About") },
                            onClick = showAboutDialog,
                        )
                    }
                }
            }
        }
        Text(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            text = title,
        )
    }
}

private enum class Menus {
    FILE,
    HELP
}
