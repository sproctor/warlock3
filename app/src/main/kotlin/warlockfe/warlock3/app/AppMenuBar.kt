package warlockfe.warlock3.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBarScope
import androidx.compose.ui.window.setContent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.util.toAwtColor
import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.core.script.WarlockScriptEngineRepository
import java.io.File
import javax.swing.JMenuBar

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun FrameWindowScope.AppMenuBar(
    characterId: String?,
    isConnected: Boolean,
    windowRepository: WindowRepository,
    scriptEngineRepository: WarlockScriptEngineRepository,
    runScript: (File) -> Unit,
    newWindow: () -> Unit,
    showSettings: () -> Unit,
    showUpdateDialog: () -> Unit,
    disconnect: () -> Unit,
    warlockVersion: String,
) {
    val windows by windowRepository.windows.collectAsState()
    val openWindows by windowRepository.observeOpenWindows(characterId ?: "").collectAsState(emptyList())
    var showAbout by remember { mutableStateOf(false) }
    var scriptDirectory by remember { mutableStateOf<String?>(null) }
    CustomMenuBar {
        Menu("File") {
            Item("New window", onClick = newWindow)
            Item("Settings", onClick = showSettings)
            if (characterId != null) {
                Item(
                    text = "Run script...",
                    onClick = {
                        val dialog = java.awt.FileDialog(window, "Run script")
                        if (scriptDirectory != null) {
                            dialog.directory = scriptDirectory
                        }
                        dialog.setFilenameFilter { _, name ->
                            val extension = File(name).extension
                            scriptEngineRepository.supportsExtension(extension)
                        }
                        dialog.isVisible = true
                        val fileName = dialog.file
                        if (fileName != null) {
                            scriptDirectory = dialog.directory
                            val file = File(dialog.directory, fileName)
                            runScript(file)
                        }
                    }
                )
            }
            Separator()
            Item(
                text = "Disconnect",
                enabled = isConnected,
                onClick = disconnect,
            )
        }

        if (characterId != null) {
            Menu("Windows") {
                windows.values.forEach { window ->
                    if (window.name != "main") {
                        CheckboxItem(
                            text = window.title,
                            checked = openWindows.any { it == window.name },
                            onCheckedChange = {
                                GlobalScope.launch {
                                    if (it) {
                                        windowRepository.openWindow(characterId, window.name)
                                    } else {
                                        windowRepository.closeWindow(characterId, window.name)
                                    }
                                }
                            }
                        )
                    }
                }
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