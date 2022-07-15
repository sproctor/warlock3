package cc.warlock.warlock3.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.rememberDialogState
import cc.warlock.warlock3.app.components.AboutDialog
import cc.warlock.warlock3.core.prefs.WindowRepository
import cc.warlock.warlock3.core.window.WindowLocation
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class, ExperimentalMaterialApi::class)
@Composable
fun FrameWindowScope.AppMenuBar(
    characterId: String?,
    windowRepository: WindowRepository,
    showSettings: () -> Unit,
    disconnect: (() -> Unit)?
) {
    val windows by windowRepository.windows.collectAsState()
    val openWindows by windowRepository.observeOpenWindows(characterId ?: "").collectAsState(emptyList())
    var showAbout by remember { mutableStateOf(false) }

    MenuBar {
        Menu("File") {
            Item("Settings", onClick = showSettings)
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
        AboutDialog { showAbout = false }
    }
}