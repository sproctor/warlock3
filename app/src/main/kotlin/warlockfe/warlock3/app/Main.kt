package warlockfe.warlock3.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import androidx.room.Room
import androidx.room.RoomDatabase
import ca.gosyer.appdirs.AppDirs
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import dev.hydraulic.conveyor.control.SoftwareUpdateController
import dev.hydraulic.conveyor.control.SoftwareUpdateController.UpdateCheckException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.FileKit
import io.sentry.kotlin.multiplatform.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY
import warlockfe.warlock3.app.di.JvmAppContainer
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.macros.keyMappings
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalLogger
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.LocalWindowComponent
import warlockfe.warlock3.compose.util.insertDefaultMacrosIfNeeded
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.sge.AutoConnectResult
import warlockfe.warlock3.core.sge.SgeSettings
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.wrayth.network.NetworkSocket
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private val version = System.getProperty("app.version")

private class WarlockCommand : CliktCommand() {
    val port: Int? by option("-p", "--port", help = "Port to connect to").int()
    val host: String? by option("-H", "--host", help = "Host to connect to")
    val key: String? by option("-k", "--key", help = "Character key to connect with")
    val debug: Boolean by option("-d", "--debug", help = "Enable debug output").flag()
    val stdin: Boolean by option("--stdin", help = "Read input from stdin").flag()
    val inputFile: String? by option("-i", "--input", help = "Read input from file")
    val autoconnectName: String? by option("-c", "--connection", help = "Auto-connect to the named connection")
    val sgeHost: String by option("--sge-host", help = "Credentials/SGE host").default("eaccess.play.net")
    val sgePort: Int by option("--sge-port", help = "Credentials/SGE port").int().default(7910)
    val sgeSecure: Boolean by option("--sge-secure", help = "Credentials/SGE uses encryption").boolean().default(true)
    val width: Int? by option(
        "--width",
        help = "Window width in \"display pixels\" (1 physical pixel at 160 DPI)"
    ).int()
    val height: Int? by option(
        "--height",
        help = "Window height in \"display pixels\" (1 physical pixel at 160 DPI)"
    ).int()
    val positionX: Int? by option("-x", "--position-x", help = "Position to place the window on the X-axis").int()
    val positionY: Int? by option("-y", "--position-y", help = "Position to place the window on the Y-axis").int()

    @OptIn(FlowPreview::class)
    override fun run() {

        val loginOptions = mutableSetOf<String>()
        if (key != null) {
            loginOptions.add("key")
        }
        if (stdin) {
            loginOptions.add("stdin")
        }
        if (inputFile != null) {
            loginOptions.add("inputFile")
        }
        if (autoconnectName != null) {
            loginOptions.add("connection")
        }
        if (loginOptions.size > 1) {
            println("More than one login method was specified. Please only use one of the following methods: $loginOptions")
            exitProcess(-1)
        }

        val version = System.getProperty("app.version")
        version?.let {
            initializeSentry(version)
        }
        if (debug || version == null) {
            System.setProperty(DEFAULT_LOG_LEVEL_KEY, "DEBUG")
        }
        val logger = KotlinLogging.logger("main")

        FileKit.init("warlock")

        val credentials = if (port != null && host != null && key != null) {
            logger.debug { "Connecting to $host:$port with $key" }
            SimuGameCredentials(host = host!!, port = port!!, key = key!!)
        } else if (port != null || key != null || key != null) {
            println("If one of \"host\", \"port\", or \"key\" is specified, the other must be as well.")
            exitProcess(-1)
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

        println("Loading preferences from ${dbFile.absolutePath}")
        val databaseBuilder = getPrefsDatabaseBuilder(dbFile.absolutePath)

        val appContainer = JvmAppContainer(databaseBuilder, warlockDirs, SystemFileSystem)

        val json = Json {
            ignoreUnknownKeys = true
        }

        val skin = mutableStateOf<Map<String, SkinObject>>(emptyMap())

        appContainer.clientSettings
            .observeSkinFile()
            .onEach { skinFile ->
                val bytes = skinFile
                    ?.let { File(it) }
                    ?.takeIf { it.exists() }
                    ?.readBytes()
                    ?: Res.readBytes("files/skin.json")
                try {
                    skin.value = json.decodeFromString<Map<String, SkinObject>>(bytes.decodeToString())
                } catch (e: Exception) {
                    // TODO: notify user of error
                    logger.error(e) { "Failed to load skin file" }
                }
            }
            .launchIn(appContainer.externalScope)

        runBlocking {
            appContainer.macroRepository.migrateMacros(
                keyMappings.map { entry ->
                    entry.value to entry.key.keyCode
                }.toMap()
            )
            appContainer.macroRepository.insertDefaultMacrosIfNeeded()
        }
        val simuCert = runBlocking { Res.readBytes("files/simu.pem") }

        val clientSettings = appContainer.clientSettings
        val initialWidth = width ?: runBlocking { clientSettings.getWidth() } ?: 640
        val initialHeight = height ?: runBlocking { clientSettings.getHeight() } ?: 480
        val position = if (positionX != null && positionY != null) {
            WindowPosition(positionX?.dp ?: Dp.Unspecified, positionY?.dp ?: Dp.Unspecified)
        } else {
            WindowPosition.PlatformDefault
        }

        val sgeSettings = SgeSettings(
            host = sgeHost,
            port = sgePort,
            certificate = simuCert,
            secure = sgeSecure,
        )

        val games = mutableStateListOf(
            GameState().apply {
                if (credentials != null || stdin || inputFile != null) {
                    val windowRepository = appContainer.windowRepositoryFactory.create()
                    val streamRegistry = appContainer.streamRegistryFactory.create(windowRepository)
                    // TODO: move this somewhere we can control it
                    runBlocking {
                        try {
                            val socket = if (stdin) {
                                WarlockStreamSocket(System.`in`)
                            } else if (inputFile != null) {
                                val file = File(inputFile!!)
                                if (!file.exists()) {
                                    logger.error { "Input file does not exist: $inputFile" }
                                    exitProcess(1)
                                }
                                WarlockStreamSocket(file.inputStream())
                            } else {
                                NetworkSocket(Dispatchers.IO)
                                    .also { socket ->
                                        socket.connect(credentials!!.host, credentials.port)
                                    }
                            }
                            val client = appContainer.warlockClientFactory.createClient(
                                windowRepository = windowRepository,
                                streamRegistry = streamRegistry,
                                socket = socket,
                            )
                            client.connect(credentials?.key ?: "")
                            val viewModel =
                                appContainer.gameViewModelFactory.create(client, windowRepository, streamRegistry)
                            setScreen(
                                GameScreen.ConnectedGameState(viewModel)
                            )
                        } catch (e: IOException) {
                            logger.error(e) { "Failed to connect to Warlock" }
                        }
                    }
                } else if (autoconnectName != null) {
                    runBlocking {
                        val connection = appContainer.connectionRepository.getByName(autoconnectName!!)
                        if (connection == null) {
                            println("Invalid connection name: $autoconnectName")
                            exitProcess(-1)
                        }
                        val sgeClient = appContainer.sgeClientFactory.create()
                        val result = sgeClient.autoConnect(sgeSettings, connection)
                        sgeClient.close()
                        when (result) {
                            is AutoConnectResult.Failure -> {
                                println(result.reason)
                                exitProcess(-1)
                            }

                            is AutoConnectResult.Success ->
                                // TODO: merge with the above, and probably below
                                try {
                                    appContainer.connectToGameUseCase(
                                        credentials = result.credentials,
                                        proxySettings = connection.proxySettings,
                                        gameState = this@apply,
                                    )
                                } catch (e: Exception) {
                                    ensureActive()
                                    println("Error connecting to server: ${e.message}")
                                    exitProcess(-1)
                                }
                        }
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

            suspend fun checkUpdate() {
                if (controller != null) {
                    try {
                        currentVersion = controller.currentVersion
                        latestVersion = controller.currentVersionFromRepository

                        if (currentVersion != null && latestVersion != null && latestVersion!! > currentVersion!!) {
                            // A newer version is available
                            updateAvailable = true
                            if (controller.canTriggerUpdateCheckUI() == SoftwareUpdateController.Availability.AVAILABLE
                                && !clientSettings.getIgnoreUpdates()
                            ) {
                                showUpdateDialog = true
                            }
                        }
                    } catch (e: UpdateCheckException) {
                        // Handle exception
                        logger.error(e) { "Update check failed" }
                    }
                }
            }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    checkUpdate()
                }
            }

            CompositionLocalProvider(
                LocalLogger provides logger,
                LocalSkin provides skin.value,
            ) {
                if (showUpdateDialog) {
                    var updateSupported by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) {
                            val availability = controller?.canTriggerUpdateCheckUI()
                            if (availability == SoftwareUpdateController.Availability.AVAILABLE) {
                                updateSupported = true
                            }
                            checkUpdate()
                        }
                    }
                    DialogWindow(
                        onCloseRequest = { showUpdateDialog = false },
                        title = "Warlock update available",
                        state = rememberDialogState(width = 400.dp, height = 300.dp),
                    ) {
                        Column(Modifier.fillMaxSize().padding(8.dp)) {
                            Text("Current version: ${currentVersion?.prettyString() ?: "unknown"}")
                            Text("Latest version: ${latestVersion?.prettyString() ?: "unknown"}")
                            if (!updateSupported) {
                                Text("Automated updates are not supported for your installation")
                            }
                            Spacer(Modifier.weight(1f))
                            Row(Modifier.fillMaxWidth()) {
                                Spacer(Modifier.weight(1f))
                                val ignoreUpdates by clientSettings.observeIgnoreUpdates().collectAsState(false)
                                if (!ignoreUpdates) {
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
                                } else {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                clientSettings.putIgnoreUpdates(false)
                                            }
                                        },
                                    ) {
                                        Text("Stop ignoring updates")
                                    }
                                }
                                TextButton(
                                    onClick = { showUpdateDialog = false },
                                ) {
                                    Text("Close")
                                }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            clientSettings.putIgnoreUpdates(false)
                                        }
                                        controller.triggerUpdateCheckUI()
                                    },
                                    enabled = updateAvailable && updateSupported
                                ) {
                                    Text("Update")
                                }
                            }
                        }
                    }
                }

                games.forEachIndexed { index, gameState ->
                    val windowState = remember {
                        WindowState(
                            width = initialWidth.dp,
                            height = initialHeight.dp,
                            position = position
                        )
                    }
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
                                    games.add(GameState())
                                },
                                showUpdateDialog = { showUpdateDialog = true },
                                sgeSettings = sgeSettings,
                            )
                            LaunchedEffect(windowState) {
                                snapshotFlow { windowState.size }
                                    .debounce(2.seconds)
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
}

@OptIn(ExperimentalResourceApi::class)
fun main(args: Array<String>) = WarlockCommand().versionOption(version ?: "Development").main(args)

private fun GameState.getTitle(): Flow<String> {
    return when (val screen = this.screen) {
        GameScreen.Dashboard ->
            flow { emit("Dashboard") }

        is GameScreen.ConnectedGameState ->
            screen.viewModel.character.map { it?.name ?: "Loading..." }

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

fun SoftwareUpdateController.Version.prettyString(): String {
    var result = version
    if (revision > 0) {
        result += "-$revision"
    }
    return result
}