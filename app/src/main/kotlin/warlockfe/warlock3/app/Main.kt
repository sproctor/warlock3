package warlockfe.warlock3.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ca.gosyer.appdirs.AppDirs
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY
import warlockfe.warlock3.app.di.JvmAppContainer
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.window.StreamRegistryImpl
import warlockfe.warlock3.compose.util.LocalLogger
import warlockfe.warlock3.compose.util.LocalWindowComponent
import warlockfe.warlock3.compose.util.insertDefaultMacrosIfNeeded
import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.core.prefs.sql.Database
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.core.util.createDatabase
import java.io.File
import java.nio.file.Path
import javax.swing.UIManager
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.math.roundToInt

fun main(args: Array<String>) {

    val parser = ArgParser("warlock3")
    val port by parser.option(
        type = ArgType.Int,
        fullName = "port",
        shortName = "p",
        description = "Port to connect to"
    )
    val host by parser.option(
        type = ArgType.String,
        fullName = "host",
        shortName = "H",
        description = "Host to connect to"
    )
    val key by parser.option(
        type = ArgType.String,
        fullName = "key",
        shortName = "k",
        description = "Character key to connect with"
    )
    val debug by parser.option(
        type = ArgType.Boolean,
        fullName = "debug",
        shortName = "d",
        description = "Enable debug output"
    )
    parser.parse(args)

    val version = System.getProperty("app.version")
    if (debug == true || (debug == null && version == null)) {
        System.setProperty(DEFAULT_LOG_LEVEL_KEY, "DEBUG")
    }
    val logger = KotlinLogging.logger("main")

    val credentials =
        if (port != null && host != null && key != null) {
            logger.debug { "Connecting to $host:$port with $key" }
            SimuGameCredentials(host = host!!, port = port!!, key = key!!)
        } else {
            null
        }

    val appDirs = AppDirs("warlock", "WarlockFE")
    val configDir = appDirs.getUserConfigDir()
    File(configDir).mkdirs()
    val dbFilename = "$configDir/prefs.db"
    val warlockDirs = WarlockDirs(
        homeDir = System.getProperty("user.home"),
        configDir = appDirs.getUserConfigDir(),
        dataDir = appDirs.getUserDataDir(),
        logDir = appDirs.getUserLogDir(),
    )

    // Copy old config
    val oldConfigDir = System.getProperty("user.home") + "/.warlock3"
    val oldDbFilename = "$oldConfigDir/prefs.db"
    if (!File(dbFilename).exists() && File(oldDbFilename).exists()) {
        File(oldDbFilename).copyTo(File(dbFilename))
    }

    val driver = JdbcSqliteDriver(url = "jdbc:sqlite:$dbFilename", schema = Database.Schema)
    val database = createDatabase(driver)
    database.insertDefaultMacrosIfNeeded()

    val appContainer = JvmAppContainer(database, warlockDirs)
    val clientSettings = appContainer.clientSettings
    val initialWidth = runBlocking { clientSettings.getWidth() } ?: 640
    val initialHeight = runBlocking { clientSettings.getHeight() } ?: 480

    val games = mutableStateListOf(
        GameState(
            windowRepository = WindowRepository(database.windowSettingsQueries, Dispatchers.IO),
            streamRegistry = StreamRegistryImpl()
        ).apply {
            if (credentials != null) {
                val client = appContainer.warlockClientFactory.createStormFrontClient(
                    credentials,
                    windowRepository,
                    streamRegistry
                )
                client.connect()
                val viewModel = appContainer.gameViewModelFactory.create(client, windowRepository, streamRegistry)
                screen = GameScreen.ConnectedGameState(viewModel)
            }
        }
    )

    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

    application {
        CompositionLocalProvider(
            LocalLogger provides logger,
        ) {
            games.forEachIndexed { index, gameState ->
                val windowState = remember { WindowState(width = initialWidth.dp, height = initialHeight.dp) }
                Window(
                    title = "Warlock 3 - ${gameState.getTitle()}",
                    state = windowState,
                    icon = appIcon,
                    onCloseRequest = {
                        games.removeAt(index)
                        if (games.isEmpty()) {
                            exitApplication()
                        }
                    },
                ) {
                    CompositionLocalProvider(
                        LocalWindowComponent provides window
                    ) {
                        WarlockApp(
                            appContainer = appContainer,
                            gameState = gameState,
                            newWindow = {
                                games.add(
                                    GameState(
                                        windowRepository = WindowRepository(
                                            database.windowSettingsQueries,
                                            Dispatchers.IO
                                        ),
                                        streamRegistry = StreamRegistryImpl()
                                    )
                                )
                            },
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
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
private val appIcon: Painter? by lazy {
    // app.dir is set when packaged to point at our collected inputs.
    val appDirProp = System.getProperty("app.dir")
    val appDir = appDirProp?.let { Path.of(it) }
    // On Windows we should use the .ico file. On Linux, there's no native compound image format and Compose can't render SVG icons,
    // so we pick the 128x128 icon and let the frameworks/desktop environment rescale. On macOS we don't need to do anything.
    var iconPath = appDir?.resolve("app.ico")?.takeIf { it.exists() }
    iconPath = iconPath ?: appDir?.resolve("icon-square-128.png")?.takeIf { it.exists() }
    if (iconPath?.exists() == true) {
        BitmapPainter(iconPath.inputStream().readAllBytes().decodeToImageBitmap())
    } else {
        null
    }
}

private fun GameState.getTitle(): String {
    return when (val screen = this.screen) {
        GameScreen.Dashboard -> "Dashboard"
        is GameScreen.ConnectedGameState -> screen.viewModel.properties.value["character"] ?: "N/A"
        is GameScreen.NewGameState -> "New game"
        is GameScreen.ErrorState -> "Error"
    }
}