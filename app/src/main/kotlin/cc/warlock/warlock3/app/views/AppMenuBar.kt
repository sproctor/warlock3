package cc.warlock.warlock3.app.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import cc.warlock.warlock3.core.prefs.WindowRepository

@Composable
fun FrameWindowScope.AppMenuBar(
    characterId: String?,
    windowRepository: WindowRepository,
    showSettings: () -> Unit,
) {
    val windows by windowRepository.windows.collectAsState()
    val openWindows by windowRepository.observeOpenWindows(characterId ?: "").collectAsState(emptyList())

    MenuBar {
        Menu("File") {
            Item("Settings", onClick = showSettings)
        }

        Menu("Windows") {
            windows.values.forEach { window ->
                if (window.name != "main") {
                    CheckboxItem(
                        text = window.title,
                        checked = openWindows.any { it == window.name },
                        onCheckedChange = {
                            if (characterId != null) {
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