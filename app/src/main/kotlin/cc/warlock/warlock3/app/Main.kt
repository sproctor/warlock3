package cc.warlock.warlock3.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import cc.warlock.warlock3.app.config.ClientSpec
import cc.warlock.warlock3.app.config.SgeSpec
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.hocon
import com.uchuhimo.konf.source.hocon.toHocon
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File
import kotlin.math.roundToInt

private val preferencesFile = File(System.getProperty("user.home") + "/.warlock3/preferences.conf")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    preferencesFile.parentFile.mkdirs()
    preferencesFile.createNewFile()
    val config = Config {
        addSpec(SgeSpec)
        addSpec(ClientSpec)
    }
        .from.hocon.watchFile(preferencesFile)
    val initialWidth = config[ClientSpec.width]
    val initialHeight = config[ClientSpec.height]
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
                    config = config,
                    saveConfig = { updater -> updateConfig(config, updater) })
            }
        }
        LaunchedEffect(windowState) {
            snapshotFlow { windowState.size }
                .onEach { size ->
                    updateConfig(config) { updatedConfig ->
                        updatedConfig[ClientSpec.width] = size.width.value.roundToInt()
                        updatedConfig[ClientSpec.height] = size.height.value.roundToInt()
                    }
                }
                .launchIn(this)
        }
    }
}

fun updateConfig(
    config: Config,
    updates: (Config) -> Unit
) {
    // first, lock file
    preferencesFile.outputStream().channel.lock().use {
        // then apply the new update
        updates(config)

        // Then save the changes to preference
        config.toHocon.toFile(preferencesFile)
    }
}