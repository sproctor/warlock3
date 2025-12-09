package warlockfe.warlock3.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.utils.toKotlinxIoPath
import kotlinx.io.files.Path
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls
import org.jetbrains.jewel.window.utils.DesktopPlatform
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.space_dashboard_filled
import warlockfe.warlock3.compose.generated.resources.space_dashboard_outlined

@Suppress("DEPRECATION")
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
) {
    val scriptFilePickerLauncher = rememberFilePickerLauncher(
        title = "Run script",
        directory = scriptDirectory?.let { PlatformFile(it) },
    ) { file ->
        if (file != null) {
            runScript(file.file.toKotlinxIoPath())
        }
    }
    TitleBar(
        modifier = Modifier.newFullscreenControls(),
    ) {
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
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = null,
                    )
                }
            }
            if (DesktopPlatform.Current == DesktopPlatform.MacOS) {
                AppMenuBar(
                    isConnected = isConnected,
                    openNewWindow = openNewWindow,
                    showSettingsDialog = showSettingsDialog,
                    disconnect = disconnect,
                    runScript = scriptFilePickerLauncher::launch,
                    showUpdateDialog = showUpdateDialog,
                    showAboutDialog = showAboutDialog,
                )
            } else {
                var active by remember { mutableStateOf(false) }
                var currentMenu by remember { mutableStateOf<Menus?>(null) }
                Box {
                    TextButton(
                        modifier = Modifier.onHover {
                            currentMenu = Menus.FILE
                        },
                        onClick = {
                            active = !active
                            currentMenu = Menus.FILE
                        },
                    ) {
                        Text("File")
                    }
                    if (active && currentMenu == Menus.FILE) {
                        PopupMenu(
                            onDismissRequest = {
                                active = false
                                true
                            },
                            horizontalAlignment = Alignment.Start
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
                                enabled = isConnected,
                                onClick = disconnect,
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
                Box {
                    TextButton(
                        modifier = Modifier.onHover {
                            currentMenu = Menus.HELP
                        },
                        onClick = {
                            active = !active
                            currentMenu = Menus.HELP
                        },
                    ) {
                        Text("Help")
                    }
                    if (active && currentMenu == Menus.HELP) {
                        PopupMenu(
                            onDismissRequest = {
                                active = false
                                true
                            },
                            horizontalAlignment = Alignment.Start
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
    HELP
}