package cc.warlock.warlock3.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.hocon
import com.uchuhimo.konf.source.hocon.toHocon
import com.uchuhimo.konf.source.json.toJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.*
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
fun main() {

    val configWatcher = ConfigWatcher()
    val initialConfig = configWatcher.configState.value

    val initialWidth = initialConfig[ClientSpec.width]
    val initialHeight = initialConfig[ClientSpec.height]
    val windowState = WindowState(width = initialWidth.dp, height = initialHeight.dp)

    singleWindowApplication(
        title = "Warlock 3",
        state = windowState,
        icon = BitmapPainter(useResource("images/icon.png", ::loadImageBitmap)),
    ) {
        WarlockTheme {
            Surface(Modifier.fillMaxSize()) {
                WarlockApp(
                    state = rememberGameState(),
                    config = configWatcher.configState,
                    saveConfig = { updater -> configWatcher.updateConfig(updater) })
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




