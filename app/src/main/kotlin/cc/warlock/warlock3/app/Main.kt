package cc.warlock.warlock3.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cc.warlock.warlock3.app.config.ClientSpec
import cc.warlock.warlock3.app.config.SgeSpec
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.hocon
import com.uchuhimo.konf.source.hocon.toHocon
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File
import kotlin.math.roundToInt

val preferencesFile = File(System.getProperty("user.home") + "/.warlock3/preferences.conf")

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    val config = remember {
        preferencesFile.parentFile.mkdirs()
        preferencesFile.createNewFile()
        Config {
            addSpec(SgeSpec)
            addSpec(ClientSpec)
        }
            .from.hocon.file(preferencesFile)
    }
    val initialWidth = remember { config[ClientSpec.width] }
    val initialHeight = remember { config[ClientSpec.height] }
    val windowState = rememberWindowState(width = initialWidth.dp, height = initialHeight.dp)
    val windowIcon = painterResource("images/icon.png")
    Window(
        onCloseRequest = ::exitApplication,
        title = "Warlock 3",
        state = windowState,
        icon = windowIcon,
    ) {
        WarlockTheme {
            Surface(Modifier.fillMaxSize()) {
                WarlockApp(rememberGameState(), config)
            }
        }
    }
    LaunchedEffect(windowState) {
        snapshotFlow { windowState.size }
            .onEach { size ->
                config[ClientSpec.width] = size.width.value.roundToInt()
                config[ClientSpec.height] = size.height.value.roundToInt()
                config.toHocon.toFile(preferencesFile)
            }
            .launchIn(this)
    }
}