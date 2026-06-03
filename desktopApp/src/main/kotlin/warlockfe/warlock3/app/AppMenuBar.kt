package warlockfe.warlock3.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar

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
    exportCharacterSettings: (() -> Unit)?,
    importSettings: () -> Unit,
    importWraythSettings: () -> Unit,
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
                text = "Export all settings...",
                onClick = exportSettings,
            )
            Item(
                text = "Export current character...",
                enabled = exportCharacterSettings != null,
                onClick = { exportCharacterSettings?.invoke() },
            )
            Item(
                text = "Import settings...",
                onClick = importSettings,
            )
            Item(
                text = "Import wrayth settings...",
                onClick = importWraythSettings,
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
