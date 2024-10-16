package warlockfe.warlock3.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import app.cash.sqldelight.adapter.primitive.FloatColumnAdapter
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ca.gosyer.appdirs.AppDirs
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY
import warlockfe.warlock3.app.di.JvmAppContainer
import warlockfe.warlock3.compose.util.LocalLogger
import warlockfe.warlock3.compose.util.insertDefaultMacrosIfNeeded
import warlockfe.warlock3.core.prefs.adapters.LocationAdapter
import warlockfe.warlock3.core.prefs.adapters.UUIDAdapter
import warlockfe.warlock3.core.prefs.adapters.WarlockColorAdapter
import warlockfe.warlock3.core.prefs.sql.Alias
import warlockfe.warlock3.core.prefs.sql.Alteration
import warlockfe.warlock3.core.prefs.sql.Database
import warlockfe.warlock3.core.prefs.sql.Highlight
import warlockfe.warlock3.core.prefs.sql.HighlightStyle
import warlockfe.warlock3.core.prefs.sql.PresetStyle
import warlockfe.warlock3.core.prefs.sql.WindowSettings
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.util.WarlockDirs
import java.io.File
import java.nio.file.Path
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
    val database = Database(
        driver = driver,
        HighlightAdapter = Highlight.Adapter(idAdapter = UUIDAdapter),
        HighlightStyleAdapter = HighlightStyle.Adapter(
            highlightIdAdapter = UUIDAdapter,
            groupNumberAdapter = IntColumnAdapter,
            textColorAdapter = WarlockColorAdapter,
            backgroundColorAdapter = WarlockColorAdapter,
            fontSizeAdapter = FloatColumnAdapter,
        ),
        PresetStyleAdapter = PresetStyle.Adapter(
            textColorAdapter = WarlockColorAdapter,
            backgroundColorAdapter = WarlockColorAdapter,
            fontSizeAdapter = FloatColumnAdapter,
        ),
        WindowSettingsAdapter = WindowSettings.Adapter(
            widthAdapter = IntColumnAdapter,
            heightAdapter = IntColumnAdapter,
            locationAdapter = LocationAdapter,
            positionAdapter = IntColumnAdapter,
            textColorAdapter = WarlockColorAdapter,
            backgroundColorAdapter = WarlockColorAdapter,
            fontSizeAdapter = FloatColumnAdapter,
        ),
        AliasAdapter = Alias.Adapter(
            idAdapter = UUIDAdapter,
        ),
        AlterationAdapter = Alteration.Adapter(
            idAdapter = UUIDAdapter,
        )
    )
    database.insertDefaultMacrosIfNeeded()

    val appContainer = JvmAppContainer(database, warlockDirs)
    val clientSettings = appContainer.clientSettings
    val initialWidth = runBlocking { clientSettings.getWidth() } ?: 640
    val initialHeight = runBlocking { clientSettings.getHeight() } ?: 480
    val windowState = WindowState(width = initialWidth.dp, height = initialHeight.dp)

    application {
        CompositionLocalProvider(
            LocalLogger provides logger
        ) {
            Window(
                title = "Warlock 3",
                state = windowState,
                icon = appIcon,
                onCloseRequest = ::exitApplication,
            ) {
                WarlockApp(
                    appContainer = appContainer,
                    credentials = credentials,
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

private val appIcon: Painter? by lazy {
    // app.dir is set when packaged to point at our collected inputs.
    val appDirProp = System.getProperty("app.dir")
    val appDir = appDirProp?.let { Path.of(it) }
    // On Windows we should use the .ico file. On Linux, there's no native compound image format and Compose can't render SVG icons,
    // so we pick the 128x128 icon and let the frameworks/desktop environment rescale. On macOS we don't need to do anything.
    var iconPath = appDir?.resolve("app.ico")?.takeIf { it.exists() }
    iconPath = iconPath ?: appDir?.resolve("icon-square-128.png")?.takeIf { it.exists() }
    if (iconPath?.exists() == true) {
        BitmapPainter(iconPath.inputStream().buffered().use { loadImageBitmap(it) })
    } else {
        null
    }
}
