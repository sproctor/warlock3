package warlockfe.warlock3.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
import warlockfe.warlock3.core.prefs.WindowRepository
import java.io.File
import javax.swing.JMenuBar

@Composable
fun FrameWindowScope.AppMenuBar(
    characterId: String?,
    isConnected: Boolean,
    windowRepository: WindowRepository,
    runScript: (File) -> Unit,
    newWindow: suspend () -> Unit,
    showSettings: suspend () -> Unit,
    showUpdateDialog: suspend () -> Unit,
    disconnect: suspend () -> Unit,
    warlockVersion: String,
) {
    val windows by windowRepository.windows.collectAsState()
    val openWindows by windowRepository.observeOpenWindows(characterId ?: "").collectAsState(emptyList())
    var showAbout by remember { mutableStateOf(false) }
    var scriptDirectory by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    CustomMenuBar {
        Menu("File") {
            Item("New window", onClick = { scope.launch { newWindow() } })
            Item(
                text = "Settings",
                onClick = {
                    scope.launch { showSettings() }
                }
            )
            Item(
                text = "Run script...",
                enabled = characterId != null,
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
                    scope.launch { disconnect() }
                },
            )
        }

        Menu(
            text = "Windows",
            enabled = characterId != null && windows.values.isNotEmpty(),
        ) {
            windows.values.sortedBy { it.name }.forEach { window ->
                if (window.name != "main") {
                    CheckboxItem(
                        text = window.title,
                        checked = openWindows.any { it == window.name },
                        onCheckedChange = {
                            scope.launch {
                                if (characterId != null) {
                                    if (it) {
                                        windowRepository.openWindow(characterId, window.name)
                                    } else {
                                        windowRepository.closeWindow(characterId, window.name)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
        Menu("Help") {
            Item("Updates") {
                scope.launch {
                    showUpdateDialog()
                }
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