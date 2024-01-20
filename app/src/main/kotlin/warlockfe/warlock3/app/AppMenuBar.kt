package warlockfe.warlock3.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import warlockfe.warlock3.WarlockBuildConfig
import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.scripting.WarlockScriptEngineRegistry
import java.io.File

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun FrameWindowScope.AppMenuBar(
    characterId: String?,
    windowRepository: WindowRepository,
    scriptEngineRegistry: WarlockScriptEngineRegistry,
    runScript: (File) -> Unit,
    showSettings: () -> Unit,
    disconnect: (() -> Unit)?
) {
    val windows by windowRepository.windows.collectAsState()
    val openWindows by windowRepository.observeOpenWindows(characterId ?: "").collectAsState(emptyList())
    var showAbout by remember { mutableStateOf(false) }
    var scriptDirectory by remember { mutableStateOf<String?>(null) }
    MenuBar {
        Menu("File") {
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
                            scriptEngineRegistry.getEngineForExtension(extension) != null
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
            if (disconnect != null) {
                Divider(Modifier.fillMaxWidth())
                Item("Disconnect", onClick = disconnect)
            }
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
            Item("About") {
                showAbout = true
            }
        }
    }
    if (showAbout) {
        AboutDialog(WarlockBuildConfig.warlockVersion) { showAbout = false }
    }
}