package cc.warlock.warlock3.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import cc.warlock.warlock3.app.config.ClientSpec
import cc.warlock.warlock3.app.config.ConfigWatcher
import cc.warlock.warlock3.app.config.SgeSpec
import cc.warlock.warlock3.app.views.appMenuBar
import cc.warlock.warlock3.app.views.settings.SettingsDialog
import cc.warlock.warlock3.core.script.VariableRegistry
import cc.warlock.warlock3.core.util.toCaseInsensitiveMap
import cc.warlock.warlock3.core.window.WindowRegistry
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.hocon
import com.uchuhimo.konf.source.hocon.toHocon
import com.uchuhimo.konf.source.json.toJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.pushingpixels.aurora.component.model.CommandGroup
import org.pushingpixels.aurora.theming.marinerSkin
import org.pushingpixels.aurora.window.AuroraWindow
import org.pushingpixels.aurora.window.auroraApplication
import java.io.File
import java.nio.file.*
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

    AuroraWindow(
        title = "Warlock 3",
        skin = marinerSkin(),
        state = windowState,
        icon = BitmapPainter(useResource("images/icon.png", ::loadImageBitmap)),
        undecorated = true,
        onCloseRequest = ::exitApplication,
        menuCommands = appMenuBar(
            windowRegistry = windowRegistry,
            showSettings = { showSettings = true }
        )
    ) {
        WarlockTheme {
            Surface(Modifier.fillMaxSize()) {
                WarlockApp(
                    state = rememberGameState(),
                    config = configWatcher.configState,
                    saveConfig = { updater -> configWatcher.updateConfig(updater) },
                    showSettings = showSettings,
                    closeSettings = { showSettings = false },
                    windowRegistry = windowRegistry,
                )
            }
        }
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




