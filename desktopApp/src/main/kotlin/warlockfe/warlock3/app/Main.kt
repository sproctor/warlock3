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
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import io.github.kdroidfilter.nucleus.core.runtime.NucleusApp
import io.github.kdroidfilter.nucleus.core.runtime.Platform
import io.github.kdroidfilter.nucleus.updater.NucleusUpdater
import io.github.kdroidfilter.nucleus.updater.UpdateInfo
import io.github.kdroidfilter.nucleus.updater.UpdateResult
import io.github.kdroidfilter.nucleus.updater.provider.UpdateProvider
import io.github.kdroidfilter.nucleus.window.jewel.JewelDecoratedWindow
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
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
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.LocalWindowComponent
import warlockfe.warlock3.compose.util.insertDefaultMacrosIfNeeded
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.sge.AutoConnectResult
import warlockfe.warlock3.core.sge.SgeSettings
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.wrayth.network.NetworkSocket
import java.awt.Dimension
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private val version = NucleusApp.version

private class WarlockCommand : CliktCommand() {
    val port: Int? by option("-p", "--port", help = "Port to connect to").int()
    val host: String? by option("-H", "--host", help = "Host to connect to")
    val key: String? by option("-k", "--key", help = "Character key to connect with")
    val debug: Boolean by option("-d", "--debug", help = "Enable debug output").flag()
    val stdin: Boolean by option("--stdin", help = "Read input from stdin").flag()
    val inputFile: String? by option("-i", "--input", help = "Read input from file")
    val autoConnectName: String? by option("-c", "--connection", help = "Auto-connect to the named connection")
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
        if (loginOptions.size > 1) {
            println("More than one login method was specified. Please only use one of the following methods: $loginOptions")
            exitProcess(-1)
        }

        version?.let {
            initializeSentry(version)
        }
        if (debug || version == null) {
            System.setProperty(DEFAULT_LOG_LEVEL_KEY, "DEBUG")
            Logger.setMinSeverity(Severity.Debug)
        } else {
            Logger.setMinSeverity(Severity.Info)
        }
        val logger = Logger.withTag("Main")

        FileKit.init("warlock")

        val credentials =
            if (port != null && host != null && key != null) {
                logger.d { "Connecting to $host:$port with $key" }
                SimuGameCredentials(host = host!!, port = port!!, key = key!!)
            } else if (port != null || host != null || key != null) {
                println("If one of \"host\", \"port\", or \"key\" is specified, the other must be as well.")
                exitProcess(-1)
            } else {
                null
            }

        val appDirs =
            AppDirs {
                appName = "warlock"
                appAuthor = "WarlockFE"
            }
        val configDir = appDirs.getUserConfigDir()
        File(configDir).mkdirs()
        val dbFile = File(configDir, "prefs.db")
        val warlockDirs =
            WarlockDirs(
                homeDir = System.getProperty("user.home"),
                configDir = appDirs.getUserConfigDir(),
                dataDir = appDirs.getUserDataDir(),
                logDir = appDirs.getUserLogDir(),
            )

        println("Loading preferences from ${dbFile.absolutePath}")
        val databaseBuilder = getPrefsDatabaseBuilder(dbFile.absolutePath)

        val appContainer = JvmAppContainer(databaseBuilder, warlockDirs, SystemFileSystem)

        val json =
            Json {
                ignoreUnknownKeys = true
            }

        val skin = mutableStateOf<Map<String, SkinObject>>(emptyMap())

        appContainer.clientSettings
            .observeSkinFile()
            .onEach { skinFile ->
                val bytes =
                    skinFile
                        ?.let { File(it) }
                        ?.takeIf { it.exists() }
                        ?.readBytes()
                        ?: Res.readBytes("files/skin.json")
                try {
                    skin.value = json.decodeFromString<Map<String, SkinObject>>(bytes.decodeToString())
                } catch (e: Exception) {
                    // TODO: notify user of error
                    logger.e(e) { "Failed to load skin file" }
                }
            }.launchIn(appContainer.externalScope)

        runBlocking {
            appContainer.macroRepository.migrateMacros(KeyboardKeyMappings.reverseKeyCodeMap)
            appContainer.macroRepository.insertDefaultMacrosIfNeeded()
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
                },
            )

        // Workaround for https://issuetracker.google.com/issues/399134381
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

        val httpClient = HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val updater =
            NucleusUpdater {
                provider = CustomGitHubProvider(owner = "sproctor", repo = "warlock3", httpClient = httpClient)
                channel = when {
                    currentVersion.contains("beta") -> "beta"
                    currentVersion.contains("alpha") -> "alpha"
                    else -> "latest"
                }
            }
        val updateSupported = updater.isUpdateSupported()

        application {
            val themeSetting by appContainer.clientSettings.observeTheme().collectAsState(ThemeSetting.AUTO)
            val darkMode =
                when (themeSetting) {
                    ThemeSetting.AUTO -> isSystemInDarkTheme()
                    ThemeSetting.LIGHT -> false
                    ThemeSetting.DARK -> true
                }
            WarlockDesktopTheme(isDark = darkMode) {
                var showUpdateDialog by remember { mutableStateOf(false) }
                var availableUpdate: UpdateInfo? by remember { mutableStateOf(null) }
                var downloadProgress: Double? by remember { mutableStateOf(null) }
                var downloadedFile: File? by remember { mutableStateOf(null) }
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
                    DialogWindow(
                        onCloseRequest = { showUpdateDialog = false },
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
                                                showUpdateDialog = false
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
                                    onClick = { showUpdateDialog = false },
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
                    // app.dir is set when packaged to point at our collected inputs.
                    val appIcon =
                        remember {
                            System
                                .getProperty("app.dir")
                                ?.let { Paths.get(it, "icon-512.png") }
                                ?.takeIf { it.exists() }
                                ?.inputStream()
                                ?.use { BitmapPainter(it.readAllBytes().decodeToImageBitmap()) }
                        }
                    JewelDecoratedWindow(
                        title = title,
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
                        window.minimumSize = Dimension(240, 240)
                        CompositionLocalProvider(
                            LocalWindowComponent provides window,
                            LocalSkin provides skin.value,
                        ) {
                            WarlockApp(
                                title = title,
                                appContainer = appContainer,
                                gameState = gameState,
                                openNewWindow = {
                                    games.add(GameState())
                                },
                                showUpdateDialog = { showUpdateDialog = true },
                                sgeSettings = sgeSettings,
                            )
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

private fun GameState.getTitle(): Flow<String> =
    when (val screen = this.screen) {
        GameScreen.Dashboard ->
            flow { emit("Dashboard") }

        is GameScreen.ConnectedGameState ->
            screen.viewModel.character.map { it?.name ?: "Loading..." }

        is GameScreen.NewGameState ->
            flow { emit("New game") }

        is GameScreen.ErrorState ->
            flow { emit("Error") }
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

class CustomGitHubProvider(
    val owner: String,
    val repo: String,
    val httpClient: HttpClient,
    val token: String? = null,
) : UpdateProvider {
    /**
     * Base URL for the GitHub REST API. Exposed as `internal` so tests in this module
     * can redirect API traffic to a local server; not part of the public API.
     */
    internal var apiBaseUrl: String = "https://api.github.com"

    override fun getUpdateMetadataUrl(
        channel: String,
        platform: Platform,
    ): String {
        val fileName = metadataFileName(channel, platform)
        if (channel.equals(LATEST_CHANNEL, ignoreCase = true)) {
            return "https://github.com/$owner/$repo/releases/latest/download/$fileName"
        }
        val tag = findLatestPrereleaseTag(channel, httpClient)
        return "https://github.com/$owner/$repo/releases/download/$tag/$fileName"
    }

    override fun getDownloadUrl(
        fileName: String,
        version: String,
    ): String = "https://github.com/$owner/$repo/releases/download/v$version/$fileName"

    override fun authHeaders(): Map<String, String> =
        if (token != null) {
            mapOf("Authorization" to "token $token")
        } else {
            emptyMap()
        }

    private fun metadataFileName(
        channel: String,
        platform: Platform,
    ): String {
        val suffix = platformSuffix(platform)
        return if (suffix.isEmpty()) "$channel.yml" else "$channel-$suffix.yml"
    }

    private fun findLatestPrereleaseTag(
        channel: String,
        httpClient: HttpClient,
    ): String {
        val builder =
            HttpRequest
                .newBuilder()
                .uri(URI.create("$apiBaseUrl/repos/$owner/$repo/releases?per_page=$PER_PAGE"))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2026-03-10")
        if (token != null) builder.header("Authorization", "Bearer $token")
        val request = builder.GET().build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val status = response.statusCode()
        if (status != HTTP_OK) {
            val rateLimitRemaining =
                response.headers().firstValue("X-RateLimit-Remaining").orElse(null)
            if (status == HTTP_FORBIDDEN && rateLimitRemaining == "0") {
                throw RuntimeException(
                    "GitHub API rate limit exceeded while listing releases for $owner/$repo. " +
                            "Configure a token to raise the limit.",
                )
            }
            throw RuntimeException("GitHub API returned HTTP $status while listing releases for $owner/$repo.")
        }

        val releases = json.decodeFromString<List<GitHubRelease>>(response.body())
        val match =
            releases.firstOrNull { release ->
                release.prerelease && tagMatchesChannel(release.tagName, channel)
            } ?: throw NoSuchElementException(
                "No release found for channel '$channel' within the most recent $PER_PAGE releases. " +
                        "Publish a fresh release on this channel.",
            )
        return match.tagName
    }

    private fun tagMatchesChannel(
        tag: String,
        channel: String,
    ): Boolean {
        val suffix = tag.substringAfter('-', missingDelimiterValue = "")
        return suffix.startsWith(channel, ignoreCase = true)
    }

    private fun platformSuffix(platform: Platform): String =
        when (platform) {
            Platform.Windows -> ""
            Platform.MacOS -> "mac"
            Platform.Linux -> "linux"
            Platform.Unknown -> ""
        }

    @Serializable
    internal data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val prerelease: Boolean,
    )

    private companion object {
        const val LATEST_CHANNEL = "latest"
        const val PER_PAGE = 100
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
        val json = Json { ignoreUnknownKeys = true }
    }
}
