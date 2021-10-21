package cc.warlock.warlock3.app.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import cc.warlock.warlock3.core.window.WindowRegistry

@Composable
fun FrameWindowScope.AppMenuBar(
    windowRegistry: WindowRegistry,
    showSettings: () -> Unit,
) {
    val windows by windowRegistry.windows.collectAsState()
    val openWindows by windowRegistry.openWindows.collectAsState()

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
                            if (it) {
                                windowRegistry.openWindow(window.name)
                            } else {
                                windowRegistry.closeWindow(window.name)
                            }
                        }
                    )
                }
            }
        }
    }
}