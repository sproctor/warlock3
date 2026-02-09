package warlockfe.warlock3.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import java.io.File

@Composable
fun FrameWindowScope.AppMenuBar(
    isConnected: Boolean,
    runScript: () -> Unit,
    openNewWindow: () -> Unit,
    showSettingsDialog: () -> Unit,
    showUpdateDialog: () -> Unit,
    disconnect: () -> Unit,
    showAboutDialog: () -> Unit,
    exportSettings: () -> Unit,
) {
    MenuBar {
        Menu("File") {
            Item("New window", onClick = openNewWindow)
            Item(
                text = "Run script...",
                enabled = isConnected,
                onClick = runScript,
            )
            Separator()
            Item(
                text = "Export settings...",
                onClick = exportSettings,
            )
            Item(
                text = "Settings",
                onClick = showSettingsDialog,
            )
            Separator()
            Item(
                text = "Disconnect",
                enabled = isConnected,
                onClick = disconnect,
            )
        }

        Menu("Help") {
            Item(
                text = "Updates",
                onClick = showUpdateDialog,
            )
            Item(
                text = "About",
                onClick = showAboutDialog,
            )
        }
    }
}
