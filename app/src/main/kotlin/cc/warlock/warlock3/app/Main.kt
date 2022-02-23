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
import cc.warlock.warlock3.app.views.AppMenuBar
import cc.warlock.warlock3.core.prefs.*
import cc.warlock.warlock3.core.prefs.adapters.UUIDAdapter
import cc.warlock.warlock3.core.prefs.adapters.WarlockColorAdapter
import cc.warlock.warlock3.core.prefs.models.PresetRepository
import cc.warlock.warlock3.core.prefs.sql.Database
import cc.warlock.warlock3.core.prefs.sql.Highlight
import cc.warlock.warlock3.core.prefs.sql.HightlightStyle
import cc.warlock.warlock3.core.prefs.sql.PresetStyle
import cc.warlock.warlock3.core.window.WindowRegistry
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
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
    val dbExists = File(dbFilename).exists()
    val driver = JdbcSqliteDriver(url = "jdbc:sqlite:$dbFilename")
    val database = Database(
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
    if (!dbExists) {
        Database.Schema.create(driver)
    }

    val variableRepository = VariableRepository(database.variableQueries, Dispatchers.IO)
    val characterRepository = CharacterRepository(database.characterQueries, Dispatchers.IO)
    val macroRepository = MacroRepository(database.macroQueries, Dispatchers.IO)
    val accountRepository = AccountRepository(database.accountQueries, Dispatchers.IO)
    val highlightRepository =
        HighlightRepository(database.highlightQueries, database.highlightStyleQueries, Dispatchers.IO)
    val presetRepository = PresetRepository(database.presetStyleQueries, Dispatchers.IO)

    val clientSettings = ClientSettingRepository(database.clientSettingQueries, Dispatchers.IO)

    val initialWidth = runBlocking { clientSettings.getWidth() } ?: 640
    val initialHeight = runBlocking { clientSettings.getHeight() } ?: 480
    val windowState = WindowState(width = initialWidth.dp, height = initialHeight.dp)
    val windowRegistry = remember { WindowRegistry() }
    var showSettings by remember { mutableStateOf(false) }

    Window(
        title = "Warlock 3",
        state = windowState,
        icon = BitmapPainter(useResource("images/icon.png", ::loadImageBitmap)),
        onCloseRequest = ::exitApplication,
    ) {
        AppMenuBar(
            windowRegistry = windowRegistry,
            showSettings = { showSettings = true }
        )
        WarlockApp(
            state = rememberGameState(),
            showSettings = showSettings,
            closeSettings = { showSettings = false },
            windowRegistry = windowRegistry,
            clientSettingRepository = clientSettings,
            accountRepository = accountRepository,
            characterRepository = characterRepository,
            variableRepository = variableRepository,
            macroRepository = macroRepository,
            highlightRepository = highlightRepository,
            presetRepository = presetRepository,
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




