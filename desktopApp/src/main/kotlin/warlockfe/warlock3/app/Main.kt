package warlockfe.warlock3.app

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import androidx.room.Room
import androidx.room.RoomDatabase
import ca.gosyer.appdirs.AppDirs
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import io.github.kdroidfilter.nucleus.updater.NucleusUpdater
import io.github.kdroidfilter.nucleus.updater.UpdateInfo
import io.github.kdroidfilter.nucleus.updater.UpdateResult
import io.github.kdroidfilter.nucleus.updater.provider.GitHubProvider
import io.github.kdroidfilter.nucleus.window.jewel.JewelDecoratedWindow
import io.github.vinceglb.filekit.FileKit
import io.sentry.kotlin.multiplatform.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.Text
import org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY
import warlockfe.warlock3.app.di.JvmAppContainer
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.theme.WarlockDesktopTheme
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.macros.KeyboardKeyMappings
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.openPrefsDatabase
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.LocalWindowComponent
import warlockfe.warlock3.compose.util.SkinLoader
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.prefs.ReleaseChannelSetting
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.MainWindowBounds
import warlockfe.warlock3.core.sge.AutoConnectResult
import warlockfe.warlock3.core.sge.SgeSettings
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.sge.parseSalCredentials
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.wrayth.network.NetworkSocket
import java.awt.Dimension
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private val version = System.getProperty("warlock.release.version")?.takeIf { it.isNotBlank() }

private class WarlockCommand : CliktCommand() {
    val port: Int? by option("-p", "--port", help = "Port to connect to").int()
    val host: String? by option("-H", "--host", help = "Host to connect to")
    val key: String? by option("-k", "--key", help = "Character key to connect with")
    val debug: Boolean by option("-d", "--debug", help = "Enable debug output").flag()
    val stdin: Boolean by option("--stdin", help = "Read input from stdin").flag()
    val inputFile: String? by option("-i", "--input", help = "Read input from file")
    val autoConnectName: String? by option("-c", "--connection", help = "Auto-connect to the named connection")
    val salFile: String? by argument("SAL_FILE", help = "Path to a .sal launch file to connect with").optional()
    val sgeHost: String by option("--sge-host", help = "Credentials/SGE host").default("eaccess.play.net")
    val sgePort: Int by option("--sge-port", help = "Credentials/SGE port").int().default(7910)
    val sgeSecure: Boolean by option("--sge-secure", help = "Credentials/SGE uses encryption").boolean().default(true)
    val width: Int? by option(
        "--width",
        help = "Window width in \"display pixels\" (1 physical pixel at 160 DPI)",
    ).int()
    val height: Int? by option(
        "--height",
        help = "Window height in \"display pixels\" (1 physical pixel at 160 DPI)",
    ).int()
    val positionX: Int? by option("-x", "--position-x", help = "Position to place the window on the X-axis").int()
    val positionY: Int? by option("-y", "--position-y", help = "Position to place the window on the Y-axis").int()

    @OptIn(FlowPreview::class, ExperimentalFoundationApi::class)
    override fun run() {
        Logger.setLogWriters(platformLogWriter())
        ComposeFoundationFlags.isNewContextMenuEnabled = true

        validateLoginOptions()

        val logger = configureLogging()

        FileKit.init("warlock")

        val credentials = parseCredentials(logger)

        val appContainer = buildAppContainer(logger)

        appContainer.clientSettings
            .observeSkinFile()
            .onEach { skinFile ->
                val bytes =
                    skinFile
                        ?.let { File(it) }
                        ?.takeIf { it.exists() }
                        ?.readBytes()
                        ?: Res.readBytes("files/skin.zip")
                try {
                    appContainer.skin.value = SkinLoader.parse(bytes)
                } catch (e: Exception) {
                    // TODO: notify user of error
                    logger.e(e) { "Failed to load skin file" }
                }
            }.launchIn(appContainer.externalScope)

        // Load config files + run the DB->TOML migration + seed default macros before any startup
        // reads below (window size, release channel, auto-connect) touch the config stores.
        runBlocking {
            appContainer.initialize()
        }
        val simuCert = runBlocking { Res.readBytes("files/simu.pem") }

        val clientSettings = appContainer.clientSettings
        val initialWidth = width ?: runBlocking { clientSettings.getWidth() }?.takeIf { it >= 240 } ?: 640
        val initialHeight = height ?: runBlocking { clientSettings.getHeight() }?.takeIf { it >= 240 } ?: 480
        val position =
            if (positionX != null && positionY != null) {
                WindowPosition(positionX?.dp ?: Dp.Unspecified, positionY?.dp ?: Dp.Unspecified)
            } else {
                WindowPosition.PlatformDefault
            }

        val sgeSettings =
            SgeSettings(
                host = sgeHost,
                port = sgePort,
                certificate = simuCert,
                secure = sgeSecure,
            )

        val games =
            mutableStateListOf(
                createInitialGameState(appContainer, credentials, sgeSettings, logger),
            )

        installUncaughtExceptionWorkaround()

        val updater = buildUpdater(clientSettings)
        val updateSupported = updater.isUpdateSupported()

        application {
            val themeSetting by appContainer.clientSettings.observeTheme().collectAsState(ThemeSetting.AUTO)
            val darkMode =
                when (themeSetting) {
                    ThemeSetting.AUTO -> isSystemInDarkTheme()
                    ThemeSetting.LIGHT -> false
                    ThemeSetting.DARK -> true
                }
            LaunchedEffect(darkMode) { appContainer.darkMode.value = darkMode }
            val skin by appContainer.skin.collectAsState()
            WarlockDesktopTheme(isDark = darkMode) {
                var showUpdateDialog by remember { mutableStateOf(false) }
                var availableUpdate: UpdateInfo? by remember { mutableStateOf(null) }
                val scope = rememberCoroutineScope()

                suspend fun checkUpdate() {
                    when (val result = updater.checkForUpdates()) {
                        is UpdateResult.Available -> {
                            availableUpdate = result.info
                            if (updateSupported && !clientSettings.getIgnoreUpdates()) {
                                showUpdateDialog = true
                            }
                        }

                        is UpdateResult.NotAvailable -> {
                            availableUpdate = null
                        }

                        is UpdateResult.Error -> {
                            logger.e(result.exception) { "Update check failed" }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        checkUpdate()
                    }
                }

                if (showUpdateDialog) {
                    UpdateDialog(
                        updater = updater,
                        updateSupported = updateSupported,
                        availableUpdate = availableUpdate,
                        clientSettings = clientSettings,
                        logger = logger,
                        onDismiss = { showUpdateDialog = false },
                    )
                }

                games.forEachIndexed { index, gameState ->
                    val windowState =
                        remember {
                            WindowState(
                                width = initialWidth.dp,
                                height = initialHeight.dp,
                                position = position,
                            )
                        }
                    val subtitle by gameState.getTitle().collectAsState("loading")
                    val title = "Warlock - $subtitle"
                    val connectedScreen = gameState.screen as? GameScreen.ConnectedGameState
                    val characterFlow = connectedScreen?.viewModel?.character ?: flowOf(null)
                    val connectedCharacter by characterFlow.collectAsState(null)
                    JewelDecoratedWindow(
                        title = title,
                        state = windowState,
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
                        window.minimumSize = Dimension(240, 240)
                        CompositionLocalProvider(
                            LocalWindowComponent provides window,
                            LocalSkin provides skin,
                        ) {
                            WarlockApp(
                                title = title,
                                warlockVersion = version ?: "Development",
                                appContainer = appContainer,
                                gameState = gameState,
                                openNewWindow = {
                                    games.add(GameState())
                                },
                                showUpdateDialog = { showUpdateDialog = true },
                                sgeSettings = sgeSettings,
                            )
                            LaunchedEffect(windowState, connectedCharacter?.id) {
                                val characterId = connectedCharacter?.id ?: return@LaunchedEffect
                                val bounds =
                                    appContainer.characterSettingsRepository
                                        .getMainWindowBounds(characterId)
                                        ?: return@LaunchedEffect
                                if (bounds.width >= 240 && bounds.height >= 240) {
                                    windowState.size = DpSize(bounds.width.dp, bounds.height.dp)
                                    windowState.position = WindowPosition(bounds.x.dp, bounds.y.dp)
                                }
                            }
                            LaunchedEffect(windowState) {
                                snapshotFlow { windowState.size }
                                    .debounce(2.seconds)
                                    .onEach { size ->
                                        val width = size.width.value.toInt()
                                        if (width >= 240) {
                                            clientSettings.putWidth(width)
                                        }
                                        val height = size.height.value.toInt()
                                        if (height >= 240) {
                                            clientSettings.putHeight(height)
                                        }
                                    }.launchIn(this)

                                snapshotFlow {
                                    val characterId = connectedCharacter?.id
                                    val position = windowState.position
                                    val size = windowState.size
                                    if (characterId != null) {
                                        characterId to
                                            MainWindowBounds(
                                                x = position.x.value.toInt(),
                                                y = position.y.value.toInt(),
                                                width = size.width.value.toInt(),
                                                height = size.height.value.toInt(),
                                            )
                                    } else {
                                        null
                                    }
                                }.debounce(2.seconds)
                                    .onEach { characterBounds ->
                                        val (characterId, bounds) = characterBounds ?: return@onEach
                                        if (bounds.width >= 240 && bounds.height >= 240) {
                                            appContainer.characterSettingsRepository.saveMainWindowBounds(characterId, bounds)
                                        }
                                    }.launchIn(this)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Fail fast if more than one mutually-exclusive login method was supplied on the command line. */
    private fun validateLoginOptions() {
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
        if (autoConnectName != null) {
            loginOptions.add("connection")
        }
        if (salFile != null) {
            loginOptions.add("sal")
        }
        if (loginOptions.size > 1) {
            println("More than one login method was specified. Please only use one of the following methods: $loginOptions")
            exitProcess(-1)
        }
    }

    private fun configureLogging(): Logger {
        version?.let {
            initializeSentry(version)
        }
        if (debug || version == null) {
            System.setProperty(DEFAULT_LOG_LEVEL_KEY, "DEBUG")
            Logger.setMinSeverity(Severity.Debug)
        } else {
            Logger.setMinSeverity(Severity.Info)
        }
        return Logger.withTag("Main")
    }

    private fun parseCredentials(logger: Logger): SimuGameCredentials? =
        if (salFile != null) {
            val file = File(salFile!!)
            if (!file.exists()) {
                println("SAL file does not exist: $salFile")
                exitProcess(-1)
            }
            try {
                parseSalCredentials(file.readText()).also {
                    logger.d { "Connecting to ${it.host}:${it.port} from .sal file" }
                }
            } catch (e: Exception) {
                println("Failed to parse .sal file: ${e.message}")
                exitProcess(-1)
            }
        } else if (port != null && host != null && key != null) {
            logger.d { "Connecting to $host:$port with $key" }
            SimuGameCredentials(host = host!!, port = port!!, key = key!!)
        } else if (port != null || host != null || key != null) {
            println("If one of \"host\", \"port\", or \"key\" is specified, the other must be as well.")
            exitProcess(-1)
        } else {
            null
        }

    private fun buildAppContainer(logger: Logger): JvmAppContainer {
        val appDirs =
            AppDirs {
                appName = "warlock"
                appAuthor = "WarlockFE"
            }
        val configDir = appDirs.getUserConfigDir()
        File(configDir).mkdirs()
        val databaseDirectory = kotlinx.io.files.Path(configDir)
        migrateLegacyWindowsPrefsDb(configDir, logger)

        val warlockDirs =
            WarlockDirs(
                homeDir = System.getProperty("user.home"),
                configDir = appDirs.getUserConfigDir(),
                dataDir = appDirs.getUserDataDir(),
                logDir = appDirs.getUserLogDir(),
            )

        println("Loading preferences from $configDir")
        val database =
            openPrefsDatabase(
                directory = databaseDirectory,
                fileSystem = SystemFileSystem,
                builderFactory = ::getPrefsDatabaseBuilder,
            )

        return JvmAppContainer(database, warlockDirs, SystemFileSystem)
    }

    /** Build the initial window's [GameState], performing the blocking initial connection if one was requested. */
    private fun createInitialGameState(
        appContainer: JvmAppContainer,
        credentials: SimuGameCredentials?,
        sgeSettings: SgeSettings,
        logger: Logger,
    ): GameState =
        GameState().apply {
            if (credentials != null || stdin || inputFile != null) {
                val windowRegistry = appContainer.windowRegistryFactory.create()
                // TODO: move this somewhere we can control it
                runBlocking {
                    try {
                        val socket =
                            if (stdin) {
                                WarlockStreamSocket(System.`in`)
                            } else if (inputFile != null) {
                                val file = File(inputFile!!)
                                if (!file.exists()) {
                                    logger.e { "Input file does not exist: $inputFile" }
                                    exitProcess(1)
                                }
                                WarlockStreamSocket(file.inputStream())
                            } else {
                                NetworkSocket(Dispatchers.IO)
                                    .also { socket ->
                                        socket.connect(credentials!!.host, credentials.port)
                                    }
                            }
                        val client =
                            appContainer.warlockClientFactory.createClient(
                                windowRegistry = windowRegistry,
                                socket = socket,
                            )
                        client.connect(credentials?.key ?: "")
                        val viewModel =
                            appContainer.gameViewModelFactory.create(client, windowRegistry)
                        setScreen(
                            GameScreen.ConnectedGameState(viewModel),
                        )
                    } catch (e: IOException) {
                        logger.e(e) { "Failed to connect to Warlock" }
                    }
                }
            } else if (autoConnectName != null) {
                runBlocking {
                    val connection = appContainer.connectionRepository.getByName(autoConnectName!!)
                    if (connection == null) {
                        println("Invalid connection name: $autoConnectName")
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

                        is AutoConnectResult.Success -> {
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
        }

    private fun buildUpdater(clientSettings: ClientSettingRepository): NucleusUpdater {
        val resolvedVersion = version ?: "0.0.0"
        val versionChannel =
            when {
                resolvedVersion.contains("beta") -> "beta"
                resolvedVersion.contains("alpha") -> "alpha"
                else -> "latest"
            }
        val channelSetting = runBlocking { clientSettings.getReleaseChannel() }
        val selectedChannel =
            when (channelSetting) {
                ReleaseChannelSetting.CURRENT -> versionChannel
                ReleaseChannelSetting.STABLE -> "latest"
                ReleaseChannelSetting.BETA -> "beta"
                ReleaseChannelSetting.ALPHA -> "alpha"
            }
        return NucleusUpdater {
            provider = GitHubProvider(owner = "sproctor", repo = "warlock3")
            channel = selectedChannel
            currentVersion = resolvedVersion
        }
    }

    // Workaround for https://issuetracker.google.com/issues/399134381
    private fun installUncaughtExceptionWorkaround() {
        val existingHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val cause = throwable.cause ?: throwable
            val isKnownComposeBug =
                cause is NoSuchElementException &&
                    cause.message?.contains("Cannot find value for key") == true

            if (isKnownComposeBug) {
                // Swallow silently — known upstream bug, see https://issuetracker.google.com/issues/399134381
            } else {
                existingHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
fun main(args: Array<String>) = WarlockCommand().versionOption(version ?: "Development").main(args)

@Composable
private fun UpdateDialog(
    updater: NucleusUpdater,
    updateSupported: Boolean,
    availableUpdate: UpdateInfo?,
    clientSettings: ClientSettingRepository,
    logger: Logger,
    onDismiss: () -> Unit,
) {
    var downloadProgress: Double? by remember { mutableStateOf(null) }
    var downloadedFile: File? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    DialogWindow(
        onCloseRequest = onDismiss,
        title = "Warlock update available",
        state = rememberDialogState(width = 400.dp, height = 300.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground)
                .padding(8.dp),
        ) {
            Text("Current version: ${updater.currentVersion}")
            if (updateSupported) {
                Text("Update version: ${availableUpdate?.version ?: "No update available"}")
            } else {
                Text("Automated updates are not supported for your installation")
            }
            downloadProgress?.let { percent ->
                Spacer(Modifier.padding(top = 8.dp))
                Text("Downloading: ${percent.toInt()}%")
                HorizontalProgressBar(
                    progress = (percent / 100.0).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                val ignoreUpdates by clientSettings
                    .observeIgnoreUpdates()
                    .collectAsState(false)
                if (!ignoreUpdates) {
                    WarlockOutlinedButton(
                        onClick = {
                            scope.launch {
                                clientSettings.putIgnoreUpdates(true)
                                onDismiss()
                            }
                        },
                        text = "Ignore updates",
                    )
                } else {
                    WarlockOutlinedButton(
                        onClick = {
                            scope.launch {
                                clientSettings.putIgnoreUpdates(false)
                            }
                        },
                        text = "Stop ignoring updates",
                    )
                }
                Spacer(Modifier.padding(horizontal = 4.dp))
                WarlockOutlinedButton(
                    onClick = onDismiss,
                    text = "Close",
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                WarlockButton(
                    onClick = {
                        val info = availableUpdate ?: return@WarlockButton
                        val file = downloadedFile
                        if (file != null) {
                            updater.installAndRestart(file)
                            return@WarlockButton
                        }
                        scope.launch {
                            clientSettings.putIgnoreUpdates(false)
                            try {
                                updater.downloadUpdate(info).collect { progress ->
                                    downloadProgress = progress.percent
                                    progress.file?.let { downloadedFile = it }
                                }
                                downloadedFile?.let { updater.installAndRestart(it) }
                            } catch (e: Exception) {
                                ensureActive()
                                logger.e(e) { "Update download failed" }
                                downloadProgress = null
                            }
                        }
                    },
                    enabled = availableUpdate != null && updateSupported && downloadProgress == null,
                    text = if (downloadedFile != null) "Install & restart" else "Update",
                )
            }
        }
    }
}

private fun GameState.getTitle(): Flow<String> =
    when (val screen = this.screen) {
        GameScreen.Dashboard -> {
            flow { emit("Dashboard") }
        }

        is GameScreen.ConnectedGameState -> {
            screen.viewModel.character.map { it?.name ?: "Loading..." }
        }

        is GameScreen.NewGameState -> {
            flow { emit("New game") }
        }

        is GameScreen.ErrorState -> {
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

private fun getPrefsDatabaseBuilder(filename: String): RoomDatabase.Builder<PrefsDatabase> =
    Room.databaseBuilder<PrefsDatabase>(
        name = filename,
    )

private fun migrateLegacyWindowsPrefsDb(
    configDir: String,
    logger: Logger,
) {
    if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
        return
    }
    val configDirFile = File(configDir)
    val configDbFile = File(configDirFile, "prefs.db")
    if (configDbFile.exists()) {
        return
    }
    val snapshotPattern = Regex("""^warlock-v(\d+)\.db$""")
    val hasVersionedSnapshot =
        configDirFile.listFiles()?.any { it.isFile && snapshotPattern.matches(it.name) } ?: false
    if (hasVersionedSnapshot) {
        return
    }
    val localAppData =
        System.getenv("LOCALAPPDATA")
            ?: File(System.getProperty("user.home"), "AppData/Local").absolutePath
    val legacyDir =
        File(localAppData, "Packages/Warlock_ysy1438y9wgam/LocalCache/Local/WarlockFE")
    val legacyDb = File(legacyDir, "prefs.db")
    if (!legacyDb.exists()) {
        return
    }
    logger.i { "Migrating legacy prefs database from ${legacyDb.absolutePath} to ${configDbFile.absolutePath}" }
    try {
        legacyDb.copyTo(configDbFile, overwrite = false)
        listOf("prefs.db-wal", "prefs.db-shm").forEach { name ->
            val source = File(legacyDir, name)
            if (source.exists()) {
                source.copyTo(File(configDirFile, name), overwrite = false)
            }
        }
    } catch (e: Exception) {
        logger.e(e) { "Failed to migrate legacy prefs database" }
    }
}
