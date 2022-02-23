package cc.warlock.warlock3.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import cc.warlock.warlock3.app.di.AppContainer
import cc.warlock.warlock3.app.views.AppMenuBar
import cc.warlock.warlock3.core.prefs.adapters.UUIDAdapter
import cc.warlock.warlock3.core.prefs.adapters.WarlockColorAdapter
import cc.warlock.warlock3.core.prefs.migrateIfNeeded
import cc.warlock.warlock3.core.prefs.sql.Database
import cc.warlock.warlock3.core.prefs.sql.Highlight
import cc.warlock.warlock3.core.prefs.sql.HightlightStyle
import cc.warlock.warlock3.core.prefs.sql.PresetStyle
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {

    val configDir = System.getProperty("user.home") + "/.warlock3"
    File(configDir).mkdirs()
    val dbFilename = "$configDir/prefs.db"
    val driver = JdbcSqliteDriver(url = "jdbc:sqlite:$dbFilename")
    migrateIfNeeded(driver)
    AppContainer.database = Database(
        driver = driver,
        HighlightAdapter = Highlight.Adapter(idAdapter = UUIDAdapter),
        HightlightStyleAdapter = HightlightStyle.Adapter(
            highlightIdAdapter = UUIDAdapter,
            textColorAdapter = WarlockColorAdapter,
            backgroundColorAdapter = WarlockColorAdapter,
        ),
        PresetStyleAdapter = PresetStyle.Adapter(
            textColorAdapter = WarlockColorAdapter,
            backgroundColorAdapter = WarlockColorAdapter,
        )
    )

    val clientSettings = AppContainer.clientSettings
    val initialWidth = runBlocking { clientSettings.getWidth() } ?: 640
    val initialHeight = runBlocking { clientSettings.getHeight() } ?: 480
    val windowState = WindowState(width = initialWidth.dp, height = initialHeight.dp)
    var showSettings by remember { mutableStateOf(false) }

    Window(
        title = "Warlock 3",
        state = windowState,
        icon = BitmapPainter(useResource("images/icon.png", ::loadImageBitmap)),
        onCloseRequest = ::exitApplication,
    ) {
        val gameState = rememberGameState()
        val characterId = when (val currentState = gameState.value) {
            is GameState.ConnectedGameState -> currentState.character.id
            else -> null
        }
        AppMenuBar(
            characterId = characterId,
            windowRepository = AppContainer.windowRepository,
            showSettings = { showSettings = true }
        )
        WarlockApp(
            state = gameState,
            showSettings = showSettings,
            closeSettings = { showSettings = false },
        )
        LaunchedEffect(windowState) {
            snapshotFlow { windowState.size }
                .onEach { size ->
                    clientSettings.putWidth(size.width.value.roundToInt())
                    clientSettings.putHeight(size.height.value.roundToInt())
                }
                .launchIn(this)
        }
    }
}




