package warlockfe.warlock3.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import warlockfe.warlock3.core.window.Window
import java.io.File

@Composable
fun FrameWindowScope.AppMenuBar(
    isConnected: Boolean,
    windows: List<Window>,
    openWindows: Set<String>,
    scriptDirectory: String?,
    runScript: (File) -> Unit,
    newWindow: () -> Unit,
    showSettings: () -> Unit,
    showUpdateDialog: () -> Unit,
    disconnect: () -> Unit,
    openWindow: (String) -> Unit,
    closeWindow: (String) -> Unit,
    warlockVersion: String,
) {
    var showAbout by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scriptFilePickerLauncher = rememberFilePickerLauncher(
        title = "Run script",
        directory = scriptDirectory?.let { PlatformFile(it) },
    ) { file ->
        if (file != null) {
            runScript(file.file)
        }
    }
    MenuBar {
        Menu("File") {
            Item("New window", onClick = { newWindow() })
            Item(
                text = "Settings",
                onClick = {
                    showSettings()
                }
            )
            Item(
                text = "Run script...",
                enabled = isConnected,
                onClick = {
                    scriptFilePickerLauncher.launch()
                }
            )
            Separator()
            Item(
                text = "Disconnect",
                enabled = isConnected,
                onClick = {
                    disconnect()
                },
            )
        }

        Menu(
            text = "Windows",
            enabled = windows.isNotEmpty(),
        ) {
            windows.sortedBy { it.title }.forEach { window ->
                CheckboxItem(
                    text = window.title,
                    checked = openWindows.any { it == window.name },
                    onCheckedChange = {
                        if (it) {
                            openWindow(window.name)
                        } else {
                            closeWindow(window.name)
                        }
                    }
                )
            }
        }
        Menu("Help") {
            Item("Updates") {
                showUpdateDialog()
            }
            Item("About") {
                scope.launch {
                    showAbout = true
                }
            }
        }
    }
    if (showAbout) {
        AboutDialog(warlockVersion) { showAbout = false }
    }
}
