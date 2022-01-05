package cc.warlock.warlock3.app.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cc.warlock.warlock3.core.window.WindowRegistry
import org.pushingpixels.aurora.component.model.Command
import org.pushingpixels.aurora.component.model.CommandGroup
import org.pushingpixels.aurora.component.model.CommandMenuContentModel

@Composable
fun appMenuBar(
    windowRegistry: WindowRegistry,
    showSettings: () -> Unit,
): CommandGroup {
    val windows by windowRegistry.windows.collectAsState()
    val openWindows by windowRegistry.openWindows.collectAsState()

    return CommandGroup(
        commands = listOf(
            Command(
                text = "File",
                secondaryContentModel = CommandMenuContentModel(
                    CommandGroup(
                        commands = listOf(
                            Command(
                                text = "Settings",
                                action = showSettings
                            )
                        )
                    )
                )
            ),
            Command(
                text = "Windows",
                secondaryContentModel = CommandMenuContentModel(
                    CommandGroup(
                        commands = windows.values
                            .filter { it.name != "main" }
                            .map { window ->
                                Command(
                                    text = window.title,
                                    isActionToggle = true,
                                    isActionToggleSelected = openWindows.any { it == window.name },
                                    onTriggerActionToggleSelectedChange = {
                                        if (it) {
                                            windowRegistry.openWindow(window.name)
                                        } else {
                                            windowRegistry.closeWindow(window.name)
                                        }
                                    }
                                )
                            }
                    )
                )
            )
        )
    )
}