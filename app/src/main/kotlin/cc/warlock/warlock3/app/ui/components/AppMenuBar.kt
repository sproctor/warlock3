package cc.warlock.warlock3.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import cc.warlock.warlock3.core.prefs.WindowRepository
import cc.warlock.warlock3.core.window.WindowLocation
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun FrameWindowScope.AppMenuBar(
    characterId: String?,
    windowRepository: WindowRepository,
    showSettings: () -> Unit,
    disconnect: (() -> Unit)?
) {
    val windows by windowRepository.windows.collectAsState()
    val openWindows by windowRepository.observeOpenWindows(characterId ?: "").collectAsState(emptyList())

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
    }
}