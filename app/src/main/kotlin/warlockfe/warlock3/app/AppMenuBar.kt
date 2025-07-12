package warlockfe.warlock3.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBarScope
import androidx.compose.ui.window.setContent
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.util.toAwtColor
import warlockfe.warlock3.core.window.Window
import java.io.File
import javax.swing.JMenuBar

@Composable
fun FrameWindowScope.AppMenuBar(
    isConnected: Boolean,
    windows: List<Window>,
    openWindows: Set<String>,
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
    var scriptDirectory by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    CustomMenuBar {
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
                    scope.launch {
                        val file = FileKit.openFilePicker(
                            title = "Run script",
                            directory = scriptDirectory?.let { PlatformFile(it) },
                        )
                        if (file != null) {
                            runScript(file.file)
                        }
                    }
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
                showAbout = true
            }
        }
    }
    if (showAbout) {
        AboutDialog(warlockVersion) { showAbout = false }
    }
}

@Composable
fun FrameWindowScope.CustomMenuBar(content: @Composable MenuBarScope.() -> Unit) {
    val parentComposition = rememberCompositionContext()

    val backgroundColor = MaterialTheme.colorScheme.surface
    val foregroundColor = MaterialTheme.colorScheme.onSurface
    DisposableEffect(Unit) {
        val menu = JMenuBar()
        menu.background = backgroundColor.toAwtColor()
        menu.foreground = foregroundColor.toAwtColor()
        val composition = menu.setContent(parentComposition, content)
        window.jMenuBar = menu

        onDispose {
            window.jMenuBar = null
            composition.dispose()
        }
    }
}