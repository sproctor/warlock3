package warlockfe.warlock3.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.nucleus.core.runtime.Platform
import io.github.kdroidfilter.nucleus.window.DecoratedWindowScope
import io.github.kdroidfilter.nucleus.window.jewel.JewelTitleBar
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.utils.toKotlinxIoPath
import kotlinx.io.files.Path
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import warlockfe.warlock3.compose.desktop.shim.WarlockIconButton
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
    val scriptFilePickerLauncher =
        rememberFilePickerLauncher(
            dialogSettings = FileKitDialogSettings.createPlatformDialogSettings("Run script"),
            directory = scriptDirectory?.let { PlatformFile(it) },
        ) { file ->
            if (file != null) {
                runScript(file.file.toKotlinxIoPath())
            }
        }
    val exportFileSaveLauncher =
        rememberFileSaverLauncher { file ->
            if (file != null) {
                exportSettings(file.file)
            }
        }
    JewelTitleBar {
        Row(Modifier.align(Alignment.Start)) {
            if (isConnected) {
                WarlockIconButton(onClick = { showSideBar(!sideBarVisible) }) {
                    Image(
                        painter =
                            painterResource(
                                if (sideBarVisible) {
                                    Res.drawable.space_dashboard_filled
                                } else {
                                    Res.drawable.space_dashboard_outlined
                                },
                            ),
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
                    MenuTriggerButton(
                        text = "File",
                        onHover = { currentMenu = Menus.FILE },
                        onClick = {
                            active = !active
                            currentMenu = Menus.FILE
                        },
                    )
                    if (active && currentMenu == Menus.FILE) {
                        PopupMenu(
                            onDismissRequest = {
                                active = false
                                true
                            },
                            horizontalAlignment = Alignment.Start,
                        ) {
                            selectableItem(
                                selected = false,
                                onClick = openNewWindow,
                            ) {
                                Text("New window")
                            }
                            selectableItem(
                                selected = false,
                                enabled = isConnected,
                                onClick = scriptFilePickerLauncher::launch,
                            ) {
                                Text("Run script...")
                            }
                            separator()
                            selectableItem(
                                selected = false,
                                onClick = { exportFileSaveLauncher.launch("settings", "json") },
                            ) {
                                Text("Export settings...")
                            }
                            selectableItem(
                                selected = false,
                                onClick = showSettingsDialog,
                            ) {
                                Text("Settings...")
                            }
                            separator()
                            selectableItem(
                                selected = false,
                                enabled = isConnected,
                                onClick = disconnect,
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
                Box {
                    MenuTriggerButton(
                        text = "Help",
                        onHover = { currentMenu = Menus.HELP },
                        onClick = {
                            active = !active
                            currentMenu = Menus.HELP
                        },
                    )
                    if (active && currentMenu == Menus.HELP) {
                        PopupMenu(
                            onDismissRequest = {
                                active = false
                                true
                            },
                            horizontalAlignment = Alignment.Start,
                        ) {
                            selectableItem(
                                selected = false,
                                onClick = showUpdateDialog,
                            ) {
                                Text("Updates")
                            }
                            selectableItem(
                                selected = false,
                                onClick = showAboutDialog,
                            ) {
                                Text("About")
                            }
                        }
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
    HELP,
}

@Composable
private fun MenuTriggerButton(
    text: String,
    onHover: () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .onHover { hovered -> if (hovered) onHover() }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text)
    }
}
