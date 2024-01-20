package warlockfe.warlock3.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import warlockfe.warlock3.app.di.AppContainer
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.theme.AppTheme
import warlockfe.warlock3.core.prefs.adapters.LocationAdapter
import warlockfe.warlock3.core.prefs.adapters.UUIDAdapter
import warlockfe.warlock3.core.prefs.adapters.WarlockColorAdapter
import warlockfe.warlock3.core.prefs.insertDefaultsIfNeeded
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

    val configDir = System.getProperty("user.home") + "/.warlock3"
    File(configDir).mkdirs()
    val dbFilename = "$configDir/prefs.db"
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
    insertDefaultsIfNeeded(database)

    val appContainer = AppContainer(database)
    val clientSettings = appContainer.clientSettings
    val initialWidth = runBlocking { clientSettings.getWidth() } ?: 640
    val initialHeight = runBlocking { clientSettings.getHeight() } ?: 480
    val windowState = WindowState(width = initialWidth.dp, height = initialHeight.dp)

    val logDir = "$configDir/logs"
//    File(logDir).mkdirs()
    System.setProperty("WARLOCK_LOG_DIR", logDir)
    System.setProperty("WARLOCK_CHARACTER_ID", "unknown")

    application {
        var showSettings by remember { mutableStateOf(false) }

        AppTheme {
            CompositionLocalProvider(
                LocalScrollbarStyle provides LocalScrollbarStyle.current.copy(
                    hoverColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                    unhoverColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                )
            ) {
                Window(
                    title = "Warlock 3",
                    state = windowState,
                    icon = painterResource("images/icon.png"),
                    onCloseRequest = ::exitApplication,
                ) {
                    val clipboardManager = LocalClipboardManager.current
                    val gameState = remember {
                        val initialGameState = if (credentials != null) {
                            val client = appContainer.warlockClientFactory.createStormFrontClient(credentials)
                            client.connect()
                            val viewModel =
                                appContainer.gameViewModelFactory.create(client, clipboardManager)
                            GameState.ConnectedGameState(viewModel)
                        } else {
                            GameState.Dashboard
                        }
                        mutableStateOf(initialGameState)
                    }
                    val characterId = when (val currentState = gameState.value) {
                        is GameState.ConnectedGameState -> currentState.viewModel.client.characterId.collectAsState().value
                        else -> null
                    }
                    AppMenuBar(
                        characterId = characterId,
                        windowRepository = appContainer.windowRepository,
                        scriptEngineRegistry = appContainer.scriptEngineRegistry,
                        showSettings = { showSettings = true },
                        disconnect = null,
                        runScript = {
                            val currentGameState = gameState.value
                            if (currentGameState is GameState.ConnectedGameState) {
                                currentGameState.viewModel.runScript(it)
                            }
                        }
                    )
                    WarlockApp(
                        appContainer = appContainer,
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
        }
    }
}




