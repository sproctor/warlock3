package cc.warlock.warlock3.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import cc.warlock.warlock3.app.config.ClientSpec
import cc.warlock.warlock3.app.config.ConfigWatcher
import cc.warlock.warlock3.app.views.appMenuBar
import cc.warlock.warlock3.core.window.WindowRegistry
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.pushingpixels.aurora.theming.marinerSkin
import org.pushingpixels.aurora.window.AuroraWindow
import org.pushingpixels.aurora.window.auroraApplication
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
fun main() = auroraApplication {

    val configWatcher = ConfigWatcher()
    val initialConfig = configWatcher.configState.value

    val initialWidth = initialConfig[ClientSpec.width]
    val initialHeight = initialConfig[ClientSpec.height]
    val windowState = WindowState(width = initialWidth.dp, height = initialHeight.dp)
    val windowRegistry = remember { WindowRegistry() }
    var showSettings by remember { mutableStateOf(false) }

    val skin = marinerSkin()

    AuroraWindow(
        title = "Warlock 3",
        skin = skin,
        state = windowState,
        icon = BitmapPainter(useResource("images/icon.png", ::loadImageBitmap)),
        onCloseRequest = ::exitApplication,
        menuCommands = appMenuBar(
            windowRegistry = windowRegistry,
            showSettings = { showSettings = true }
        )
    ) {
        WarlockApp(
            skin = skin,
            state = rememberGameState(),
            config = configWatcher.configState,
            saveConfig = { updater -> configWatcher.updateConfig(updater) },
            showSettings = showSettings,
            closeSettings = { showSettings = false },
            windowRegistry = windowRegistry,
        )
        LaunchedEffect(windowState) {
            snapshotFlow { windowState.size }
                .onEach { size ->
                    configWatcher.updateConfig { updatedConfig ->
                        updatedConfig[ClientSpec.width] = size.width.value.roundToInt()
                        updatedConfig[ClientSpec.height] = size.height.value.roundToInt()
                        updatedConfig
                    }
                }
                .launchIn(this)
        }
    }
}




