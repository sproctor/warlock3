package warlockfe.warlock3.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ca.gosyer.appdirs.AppDirs
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import warlockfe.warlock3.app.di.JvmAppContainer
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
import java.io.File
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
    parser.parse(args)

    val credentials =
        if (port != null && host != null && key != null) {
            println("Connecting to $host:$port with $key")
            SimuGameCredentials(host = host!!, port = port!!, key = key!!)
        } else {
            null
        }

    val appDirs = AppDirs("warlock", "WarlockFE")
    val configDir = appDirs.getUserConfigDir()
    File(configDir).mkdirs()
    val dbFilename = "$configDir/prefs.db"

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
        ),
        PresetStyleAdapter = PresetStyle.Adapter(
            textColorAdapter = WarlockColorAdapter,
            backgroundColorAdapter = WarlockColorAdapter,
        ),
        WindowSettingsAdapter = WindowSettings.Adapter(
            widthAdapter = IntColumnAdapter,
            heightAdapter = IntColumnAdapter,
            locationAdapter = LocationAdapter,
            positionAdapter = IntColumnAdapter,
            textColorAdapter = WarlockColorAdapter,
            backgroundColorAdapter = WarlockColorAdapter,
        ),
        AliasAdapter = Alias.Adapter(
            idAdapter = UUIDAdapter,
        ),
        AlterationAdapter = Alteration.Adapter(
            idAdapter = UUIDAdapter,
        )
    )
    database.insertDefaultMacrosIfNeeded()

    val appContainer = JvmAppContainer(database, appDirs)
    val clientSettings = appContainer.clientSettings
    val initialWidth = runBlocking { clientSettings.getWidth() } ?: 640
    val initialHeight = runBlocking { clientSettings.getHeight() } ?: 480
    val windowState = WindowState(width = initialWidth.dp, height = initialHeight.dp)

    application {
        Window(
            title = "Warlock 3",
            state = windowState,
            icon = painterResource("images/icon.png"),
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




