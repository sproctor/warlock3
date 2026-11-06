package warlockfe.warlock3.app

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
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
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.core.runtime.NucleusApp
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.app.updater.UpdatePrompt
import warlockfe.warlock3.app.updater.buildUpdater
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.crash.CrashReporting
import warlockfe.warlock3.compose.desktop.shim.WarlockAlertDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.theme.WarlockDesktopTheme
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.app_icon
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.model.StartupConnection
import warlockfe.warlock3.compose.observeSkin
import warlockfe.warlock3.compose.util.SentryCrashReporter
import warlockfe.warlock3.core.prefs.SettingsUnreadableException
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.sge.SgeSettings
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.sge.parseSalCredentials
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.TimeSource

private val version = NucleusApp.version?.takeIf { it.isNotBlank() }

// Nucleus bakes the version into the app's generated properties, including for `./gradlew run`,
// where it is "0.0.0-dev". Treat any 0.0.0.x (or absent) version as a dev build with no real
// release behind it.
private val isDev = version?.startsWith("0.0.0") ?: true

private class WarlockCommand : CliktCommand() {
    val port: Int? by option("-p", "--port", help = "Port to connect to").int()
    val host: String? by option("-H", "--host", help = "Host to connect to")
    val key: String? by option("-k", "--key", help = "Character key to connect with")
    val debug: Boolean by option("-d", "--debug", help = "Enable debug output").flag()
    val stdin: Boolean by option("--stdin", help = "Read input from stdin").flag()
    val inputFiles: List<String> by option(
        "-i",
        "--input",
        help = "Read input from file; repeat to open each file in its own window",
    ).multiple()
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
    val exitOnDisconnect: Boolean by option(
        "--exit-on-disconnect",
        help = "Exit the client when the game connection disconnects",
    ).flag()

    /**
     * Starts the desktop app: crash reporting first, then the [AppContainer] with the user's
     * settings loaded (or the "Warlock cannot start" window and an exit, when they can't be read),
     * then a window for each game the command line or the last session asks for.
     */
    @OptIn(ExperimentalFoundationApi::class)
    override fun run() {
        val started = TimeSource.Monotonic.markNow()
        Logger.setLogWriters(platformLogWriter())
        ComposeFoundationFlags.isNewContextMenuEnabled = true

        validateLoginOptions()

        val logger = configureLogging()
        val crashReporting = startCrashReporting()

        FileKit.init("warlock")

        val credentials = parseCredentials(logger)

        // Settings that are on disk but unreadable stop the launch here rather than further in.
        // Every store treats missing data as "nothing saved yet", so starting anyway would mean
        // coming up with empty settings and then saving them over settings that were only
        // unreachable, not gone.
        val appContainer =
            try {
                // Loads the config files, runs the DB->TOML migration and seeds default macros before
                // any startup reads below (window size, release channel, auto-connect) touch them.
                openDesktopAppContainer(appDirs, crashReporting, logger).also { container ->
                    container.observeSkin(logger) { path ->
                        File(path).takeIf { it.exists() }?.readBytes()
                    }
                }
            } catch (e: SettingsUnreadableException) {
                reportUnreadableSettingsAndExit(e.problem, logger)
            }
        val clientSettings = appContainer.clientSettings

        val sgeSettings =
            SgeSettings(
                host = sgeHost,
                port = sgePort,
                certificate = runBlocking { Res.readBytes("files/simu.pem") },
                secure = sgeSecure,
            )
        val placement = windowPlacement(clientSettings)

        val games =
            mutableStateListOf<GameState>().apply {
                addAll(createInitialGameStates(appContainer, credentials, logger))
            }

        val updater = buildUpdater(currentVersion = version ?: "0.0.0", clientSettings = clientSettings)
        val updateSupported = updater.isUpdateSupported()

        // Not in the scripted modes (replays, benchmarks), where nobody is there to answer and a
        // modal dialog would cover the window being watched.
        val scripted = stdin || inputFiles.isNotEmpty() || exitOnDisconnect

        // Tao windows: the native event loop owns the main thread and there is no AWT in the process.
        // Multiple Warlock processes are allowed, as before, rather than forwarding a second
        // launch to the first.
        nucleusApplication(enableSingleInstance = false) {
            val themeSetting by clientSettings.observeTheme().collectAsState(ThemeSetting.AUTO)
            val darkMode =
                when (themeSetting) {
                    ThemeSetting.AUTO -> isSystemInDarkTheme()
                    ThemeSetting.LIGHT -> false
                    ThemeSetting.DARK -> true
                }
            LaunchedEffect(darkMode) { appContainer.darkMode.value = darkMode }
            val skin by appContainer.skin.collectAsState()
            // Advertise an icon on the window itself (_NET_WM_ICON on Linux). Without it, taskbars
            // fall back to a generic icon when no .desktop file is installed (e.g. a bare AppImage).
            val appIcon = painterResource(Res.drawable.app_icon)
            WarlockDesktopTheme(isDark = darkMode) {
                var showUpdateDialog by remember { mutableStateOf(false) }
                UpdatePrompt(
                    updater = updater,
                    updateSupported = updateSupported,
                    // Dev builds (version "0.0.0-dev", injected by Nucleus for `./gradlew run`) have
                    // no real release to update to, so skip the network update check.
                    checkOnLaunch = !isDev,
                    clientSettings = clientSettings,
                    logger = logger,
                    showDialog = showUpdateDialog,
                    onShowDialogChange = { showUpdateDialog = it },
                )

                // App-wide, and rendered once out here rather than per game window: the failure is
                // the whole app's (every window shares one database), so a user with three windows
                // open should be told once, not three times.
                val settingsProblem by appContainer.settingsProblems.current.collectAsState()
                settingsProblem?.let { problem ->
                    WarlockAlertDialog(
                        title = "Settings problem",
                        text = problem.message,
                        onDismissRequest = { appContainer.settingsProblems.dismiss() },
                        confirmButton = {
                            WarlockButton(
                                onClick = { appContainer.settingsProblems.dismiss() },
                                text = "OK",
                            )
                        },
                        height = 260.dp,
                    )
                }

                AppImageIntegrationPrompt(clientSettings = clientSettings, logger = logger)

                // Asked once, and until it is answered nothing is sent. Closing it without choosing
                // leaves the question for next launch. Shown from inside the first game window
                // rather than out here: a dialog opened at the application level has no owner, so
                // the main window, opened after it, comes up on top and hides it.
                val crashReportingConsent by appContainer.crashReporting.consent.collectAsState()
                var crashNoticeClosed by remember { mutableStateOf(scripted) }

                games.forEach { gameState ->
                    key(gameState) {
                        GameWindow(
                            gameState = gameState,
                            games = games,
                            appContainer = appContainer,
                            sgeSettings = sgeSettings,
                            placement = placement,
                            icon = appIcon,
                            skin = skin,
                            warlockVersion = version ?: "Development",
                            showCrashReportingNotice =
                                crashReportingConsent == null && !crashNoticeClosed && gameState == games.first(),
                            onCrashReportingNoticeDismiss = { crashNoticeClosed = true },
                            onDisconnect =
                                if (exitOnDisconnect) {
                                    { println("App ran for ${started.elapsedNow()}") }
                                } else {
                                    null
                                },
                            showUpdateDialog = { showUpdateDialog = true },
                        )
                    }
                }
            }
        }
    }

    /**
     * Where game windows open and how big: the command line's size and position when it gives them,
     * otherwise the size the last session saved.
     */
    private fun windowPlacement(clientSettings: ClientSettingRepository): GameWindowPlacement {
        val openWidth = width ?: runBlocking { clientSettings.getWidth() }?.takeIf { it >= MIN_WINDOW_DIMENSION }
        val openHeight = height ?: runBlocking { clientSettings.getHeight() }?.takeIf { it >= MIN_WINDOW_DIMENSION }
        val position =
            if (positionX != null && positionY != null) {
                WindowPosition(positionX!!.dp, positionY!!.dp)
            } else {
                WindowPosition.PlatformDefault
            }
        return GameWindowPlacement(
            size = DpSize((openWidth ?: 640).dp, (openHeight ?: 480).dp),
            position = position,
            sizeFromCommandLine = width != null || height != null,
        )
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
        if (inputFiles.isNotEmpty()) {
            loginOptions.add("inputFiles")
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

    private val appDirs =
        AppDirs {
            appName = "warlock"
            appAuthor = "WarlockFE"
        }

    /**
     * Before anything else that could fail, so a crash during startup still gets reported -- but only
     * if the user has already said they want that. On a first launch nothing is sent until they have
     * answered the notice the app shows.
     */
    private fun startCrashReporting(): CrashReporting =
        CrashReporting(
            configDir = appDirs.getUserConfigDir(),
            dataDir = appDirs.getUserDataDir(),
            fileSystem = SystemFileSystem,
            reporter =
                SentryCrashReporter(
                    platform = "desktop",
                    version = version ?: "unknown",
                    isRelease = !isDev,
                ),
        ).also { it.startIfConsented() }

    private fun configureLogging(): Logger {
        // This governs third-party logging too: libraries that log through slf4j (ktor, and
        // dbus-java behind the file dialogs on Linux) are routed into Kermit by KermitSlf4jProvider.
        if (debug || isDev) {
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

    /**
     * Build the [GameState]s to open on startup. Each one only says what to connect to: the window's
     * dashboard connects once it is on screen (see [StartupConnection]), so nothing here waits on
     * the network.
     *
     * Each `--input` file gets its own window/[GameState]; every other launch mode produces a single window.
     */
    private fun createInitialGameStates(
        appContainer: AppContainer,
        credentials: SimuGameCredentials?,
        logger: Logger,
    ): List<GameState> =
        when {
            inputFiles.isNotEmpty() -> {
                inputFiles.map { path ->
                    val file = File(path)
                    if (!file.exists()) {
                        logger.e { "Input file does not exist: $path" }
                        exitProcess(1)
                    }
                    GameState(StartupConnection.Stream { WarlockStreamSocket(file.inputStream()) })
                }
            }

            stdin -> {
                listOf(GameState(StartupConnection.Stream { WarlockStreamSocket(System.`in`) }))
            }

            credentials != null -> {
                listOf(GameState(StartupConnection.Credentials(credentials)))
            }

            autoConnectName != null -> {
                // A misspelled name is an error in the command line, so it is reported there, before
                // any window opens. Connecting is left to the window.
                val connection = runBlocking { appContainer.connectionRepository.getByName(autoConnectName!!) }
                if (connection == null) {
                    println("Invalid connection name: $autoConnectName")
                    exitProcess(-1)
                }
                listOf(GameState(StartupConnection.Stored(connection)))
            }

            else -> {
                listOf(GameState())
            }
        }
}

fun main(args: Array<String>) =
    WarlockCommand()
        .versionOption(version ?: "Development")
        // Nucleus packs the Linux AppImage via electron-builder, whose AppRun injects --no-sandbox
        // when the kernel blocks unprivileged user namespaces (Ubuntu 24.04+ default), assuming an
        // Electron binary. We're a JVM app with no sandbox to disable, so drop the flag rather than
        // abort startup on an unknown option.
        .main(args.filterNot { it == "--no-sandbox" })
