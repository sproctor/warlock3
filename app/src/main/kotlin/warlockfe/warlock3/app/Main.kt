package warlockfe.warlock3.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import androidx.room.Room
import androidx.room.RoomDatabase
import ca.gosyer.appdirs.AppDirs
import dev.hydraulic.conveyor.control.SoftwareUpdateController
import dev.hydraulic.conveyor.control.SoftwareUpdateController.UpdateCheckException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.FileKit
import io.sentry.kotlin.multiplatform.Sentry
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.IOException
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
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.util.WarlockDirs
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalResourceApi::class)
fun main(args: Array<String>) {

    val parser = ArgParser("warlock")
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
    version?.let {
        initializeSentry(version)
    }
    if (debug == true || (debug == null && version == null)) {
        System.setProperty(DEFAULT_LOG_LEVEL_KEY, "DEBUG")
    }
    val logger = KotlinLogging.logger("main")

    FileKit.init("warlock")

    val credentials =
        if (port != null && host != null && key != null) {
            logger.debug { "Connecting to $host:$port with $key" }
            SimuGameCredentials(host = host!!, port = port!!, key = key!!)
        } else {
            null
        }

    val appDirs = AppDirs {
        appName = "warlock"
        appAuthor = "WarlockFE"
    }
    val configDir = appDirs.getUserConfigDir()
    File(configDir).mkdirs()
    val dbFile = File(configDir, "prefs.db")
    val warlockDirs = WarlockDirs(
        homeDir = System.getProperty("user.home"),
        configDir = appDirs.getUserConfigDir(),
        dataDir = appDirs.getUserDataDir(),
        logDir = appDirs.getUserLogDir(),
    )

    val databaseBuilder = getPrefsDatabaseBuilder(dbFile.absolutePath)

    val appContainer = JvmAppContainer(databaseBuilder, warlockDirs)

    runBlocking {
        appContainer.macroRepository.insertDefaultMacrosIfNeeded()
    }

    val clientSettings = appContainer.clientSettings
    val initialWidth = runBlocking { clientSettings.getWidth() } ?: 640
    val initialHeight = runBlocking { clientSettings.getHeight() } ?: 480

    // TODO: Make a GameStateFactory
    val games = mutableStateListOf(
        GameState(
            windowRepository = WindowRepository(
                windowSettingsDao = appContainer.database.windowSettingsDao(),
                externalScope = appContainer.externalScope,
            ),
            streamRegistry = StreamRegistryImpl(
                mainDispatcher = Dispatchers.Main.immediate,
                externalScope = appContainer.externalScope,
                settingRepository = clientSettings,
            ),
        ).apply {
            if (credentials != null) {
                val client = appContainer.warlockClientFactory.createStormFrontClient(
                    credentials = credentials,
                    windowRepository = windowRepository,
                    streamRegistry = streamRegistry,
                )
                // TODO: move this somewhere we can control it
                runBlocking {
                    try {
                        client.connect()
                    } catch (e: IOException) {
                        logger.error(e) { "Failed to connect to Warlock" }
                    }
                    val viewModel = appContainer.gameViewModelFactory.create(client, windowRepository, streamRegistry)
                    screen = GameScreen.ConnectedGameState(viewModel)
                }
            }
        }
    )

    val controller = SoftwareUpdateController.getInstance()

    application {
        var updateAvailable by remember { mutableStateOf(false) }
        var showUpdateDialog by remember { mutableStateOf(false) }
        var currentVersion: SoftwareUpdateController.Version? by remember { mutableStateOf(null) }
        var latestVersion: SoftwareUpdateController.Version? by remember { mutableStateOf(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            if (controller != null && !clientSettings.getIgnoreUpdates()) {
                try {
                    currentVersion = controller.currentVersion ?: return@LaunchedEffect
                    latestVersion = controller.currentVersionFromRepository
                        ?: return@LaunchedEffect

                    // Compare versions using the compareTo method
                    if (latestVersion!! > currentVersion!!) {
                        // A newer version is available
                        if (controller.canTriggerUpdateCheckUI() == SoftwareUpdateController.Availability.AVAILABLE) {
                            updateAvailable = true
                            showUpdateDialog = true
                        }
                    } else {
                        // No update available or current version is newer
                    }
                } catch (e: UpdateCheckException) {
                    // Handle exception
                    logger.error(e) { "Update check failed" }
                }
            }
        }
        if (showUpdateDialog) {
            DialogWindow(
                onCloseRequest = { showUpdateDialog = false },
                title = "Warlock update available",
                state = rememberDialogState(width = 400.dp, height = 300.dp),
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    if (updateAvailable) {
                        Text("An update to Warlock is available. You are currently running \"$currentVersion\" and \"$latestVersion\" is available.")
                    } else {
                        Text("Current version: ${currentVersion ?: "unknown"}")
                        Text("Available version: ${latestVersion ?: "unknown"}")
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        val scope = rememberCoroutineScope()
                        TextButton(
                            onClick = {
                                scope.launch {
                                    clientSettings.putIgnoreUpdates(true)
                                    showUpdateDialog = false
                                }
                            },
                        ) {
                            Text("Ignore updates")
                        }
                        TextButton(
                            onClick = { showUpdateDialog = false },
                        ) {
                            Text("Close")
                        }
                        if (updateAvailable) {
                            TextButton(
                                onClick = {
                                    controller.triggerUpdateCheckUI()
                                }
                            ) {
                                Text("Update")
                            }
                        }
                    }
                }
            }
        }
        CompositionLocalProvider(
            LocalLogger provides logger,
        ) {
            games.forEachIndexed { index, gameState ->
                val windowState = remember { WindowState(width = initialWidth.dp, height = initialHeight.dp) }
                val title by gameState.getTitle().collectAsState("Loading...")
                // app.dir is set when packaged to point at our collected inputs.
                val appIcon = remember {
                    System.getProperty("app.dir")
                        ?.let { Paths.get(it, "icon-512.png") }
                        ?.takeIf { it.exists() }
                        ?.inputStream()
                        ?.use { BitmapPainter(it.readAllBytes().decodeToImageBitmap()) }
                }
                Window(
                    title = "Warlock - $title",
                    state = windowState,
                    icon = appIcon,
                    onCloseRequest = {
                        scope.launch {
                            val game = games[index]
                            val screen = game.screen
                            if (screen is GameScreen.ConnectedGameState) {
                                screen.viewModel.close()
                            }
                            games.removeAt(index)
                            if (games.isEmpty()) {
                                exitApplication()
                            }
                        }
                    },
                ) {
                    CompositionLocalProvider(
                        LocalWindowComponent provides window,
                    ) {
                        WarlockApp(
                            appContainer = appContainer,
                            gameState = gameState,
                            newWindow = {
                                games.add(
                                    GameState(
                                        windowRepository = WindowRepository(
                                            appContainer.database.windowSettingsDao(),
                                            CoroutineScope(Dispatchers.IO),
                                        ),
                                        streamRegistry = StreamRegistryImpl(
                                            mainDispatcher = Dispatchers.Main.immediate,
                                            externalScope = appContainer.externalScope,
                                            settingRepository = clientSettings,
                                        ),
                                    )
                                )
                            },
                            showUpdateDialog = { showUpdateDialog = true },
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

private fun GameState.getTitle(): Flow<String> {
    return when (val screen = this.screen) {
        GameScreen.Dashboard ->
            flow { emit("Dashboard") }

        is GameScreen.ConnectedGameState ->
            screen.viewModel.properties.map { value -> value["character"] ?: "Loading..." }.distinctUntilChanged()

        is GameScreen.NewGameState ->
            flow { emit("New game") }

        is GameScreen.ErrorState ->
            flow { emit("Error") }
    }
}

fun initializeSentry(version: String) {
    Sentry.init { options ->
        with(options) {
            dsn = "https://06169c08bd931ba4308dab95573400e2@o4508437273378816.ingest.us.sentry.io/4508437322727424"
            release = "desktop@$version"
        }
    }
}

private fun getPrefsDatabaseBuilder(filename: String): RoomDatabase.Builder<PrefsDatabase> {
    return Room.databaseBuilder<PrefsDatabase>(
        name = filename,
    )
}
