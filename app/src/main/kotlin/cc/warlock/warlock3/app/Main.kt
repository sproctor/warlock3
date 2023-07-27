package cc.warlock.warlock3.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cc.warlock.warlock3.app.di.AppContainer
import cc.warlock.warlock3.app.ui.components.AppMenuBar
import cc.warlock.warlock3.app.ui.theme.AppTheme
import cc.warlock.warlock3.core.prefs.adapters.LocationAdapter
import cc.warlock.warlock3.core.prefs.adapters.UUIDAdapter
import cc.warlock.warlock3.core.prefs.adapters.WarlockColorAdapter
import cc.warlock.warlock3.core.prefs.insertDefaultsIfNeeded
import cc.warlock.warlock3.core.prefs.migrateIfNeeded
import cc.warlock.warlock3.core.prefs.sql.*
import cc.warlock.warlock3.stormfront.network.SimuGameCredentials
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.roundToInt

fun main(args: Array<String>) {

    val parser = ArgParser("warlock3")
    val port by parser.option(ArgType.Int, fullName = "port", shortName = "p", description = "Port to connect to")
    val host by parser.option(ArgType.String, fullName = "host", shortName = "H", description = "Host to connect to")
    val key by parser.option(ArgType.String, fullName = "key", shortName = "k", description = "Character key to connect with")
    parser.parse(args)

    val credentials = when {
        port != null && host != null && key != null -> {
            println("Connecting to $host:$port with $key")
            SimuGameCredentials(host = host!!, port = port!!, key = key!!)
        }
        else -> null
    }

    val configDir = System.getProperty("user.home") + "/.warlock3"
    File(configDir).mkdirs()
    val dbFilename = "$configDir/prefs.db"
    val driver = JdbcSqliteDriver(url = "jdbc:sqlite:$dbFilename")
    runBlocking {
        migrateIfNeeded(driver, dbFilename)
    }
    AppContainer.database = Database(
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
    runBlocking {
        insertDefaultsIfNeeded(AppContainer.database)
    }

    val clientSettings = AppContainer.clientSettings
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
                            val client = StormfrontClient(
                                host = credentials.host,
                                port = credentials.port,
                                windowRepository = AppContainer.windowRepository,
                                characterRepository = AppContainer.characterRepository,
                                scriptEngineRegistry = AppContainer.scriptEngineRegistry,
                                alterationRepository = AppContainer.alterationRepository,
                                streamRegistry = AppContainer.streamRegistry,
                            )
                            client.connect(credentials.key)
                            val viewModel = AppContainer.gameViewModelFactory(client, clipboardManager)
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
                        windowRepository = AppContainer.windowRepository,
                        scriptEngineRegistry = AppContainer.scriptEngineRegistry,
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




