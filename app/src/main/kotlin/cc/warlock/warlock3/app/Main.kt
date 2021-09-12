package cc.warlock.warlock3.app

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.apache.commons.configuration2.builder.fluent.Configurations
import org.apache.commons.configuration2.ex.ConfigurationException
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    val config = remember {
        val configFile = File(System.getProperty("user.home") + "/.warlock3/sge.properties")
        configFile.parentFile.mkdirs()
        configFile.createNewFile()
        val configBuilder = Configurations()
            .propertiesBuilder(configFile.absolutePath)
        configBuilder.isAutoSave = true
        try {
            configBuilder.configuration
        } catch (e: ConfigurationException) {
            // no config
            println("No config found")
            null
        }
    }
    val initialWidth = remember { config?.getInt("main.width", 640) ?: 640 }
    val initialHeight = remember { config?.getInt("main.height", 480) ?: 480 }
    val windowState = rememberWindowState(width = initialWidth.dp, height = initialHeight.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Warlock 3",
        state = windowState,
    ) {
        MaterialTheme {
            WarlockApp(rememberGameState(), config)
        }
    }
    LaunchedEffect(windowState) {
        snapshotFlow { windowState.size }
            .onEach { size ->
                config?.setProperty("main.width", size.width.value.roundToInt())
                config?.setProperty("main.height", size.height.value.roundToInt())
            }
            .launchIn(this)
    }
}