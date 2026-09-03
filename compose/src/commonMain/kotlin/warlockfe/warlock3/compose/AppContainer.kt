package warlockfe.warlock3.compose

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import warlockfe.warlock3.compose.macros.KeyboardKeyMappings
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModelFactory
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.compose.ui.sge.SgeViewModelFactory
import warlockfe.warlock3.compose.ui.window.WindowRegistryFactory
import warlockfe.warlock3.compose.util.seedAndMigrateDefaultMacros
import warlockfe.warlock3.compose.util.toPresets
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.mudmobile.MudMobileApi
import warlockfe.warlock3.core.mudmobile.WarlockSettingsSync
import warlockfe.warlock3.core.prefs.MIGRATION_10_11
import warlockfe.warlock3.core.prefs.MIGRATION_14_16
import warlockfe.warlock3.core.prefs.MIGRATION_20_21
import warlockfe.warlock3.core.prefs.PREFS_DATABASE_VERSION
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.prefs.SettingsProblem
import warlockfe.warlock3.core.prefs.SettingsProblems
import warlockfe.warlock3.core.prefs.SettingsUnreadableException
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.config.ConfigMigration
import warlockfe.warlock3.core.prefs.describeForUser
import warlockfe.warlock3.core.prefs.repositories.AccountRepository
import warlockfe.warlock3.core.prefs.repositories.ActionRepository
import warlockfe.warlock3.core.prefs.repositories.AliasRepository
import warlockfe.warlock3.core.prefs.repositories.AlterationRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.CommandHistoryRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.ExportRepository
import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.LoggingRepository
import warlockfe.warlock3.core.prefs.repositories.MacroRepository
import warlockfe.warlock3.core.prefs.repositories.NameRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.prefs.repositories.ProgressBarSettingRepository
import warlockfe.warlock3.core.prefs.repositories.ScriptDirRepository
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.prefs.repositories.WindowSettingsRepository
import warlockfe.warlock3.core.prefs.snapshot.openVersionedDatabase
import warlockfe.warlock3.core.script.ScriptManagerFactory
import warlockfe.warlock3.core.sge.SgeClient
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.core.window.WindowRegistry
import warlockfe.warlock3.scripting.ScriptManagerFactoryImpl
import warlockfe.warlock3.scripting.WarlockScriptEngineRepositoryImpl
import warlockfe.warlock3.scripting.lua.LuaEngine
import warlockfe.warlock3.scripting.wsl.WslEngine
import warlockfe.warlock3.wrayth.network.SgeClientImpl
import warlockfe.warlock3.wrayth.network.WraythClient
import warlockfe.warlock3.wrayth.settings.WraythImporter
import warlockfe.warlock3.wrayth.util.CommandListStore

/**
 * The app-wide dependency graph, shared by every platform. The two genuinely platform-specific
 * pieces -- how sound is played and how a proxy subprocess is launched -- are injected by the
 * platform entry points; everything else is wired identically everywhere.
 */
class AppContainer(
    val database: PrefsDatabase,
    private val warlockDirs: WarlockDirs,
    private val fileSystem: FileSystem,
    private val soundPlayer: SoundPlayer,
    private val warlockProxyFactory: WarlockProxy.Factory,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val externalScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // The loaded skin and current dark-mode are pushed here by the platform entry points; the
    // skin's "presets" section (resolved for the mode) becomes the default text styles.
    val skin = MutableStateFlow<Map<String, SkinObject>>(emptyMap())
    val darkMode = MutableStateFlow(false)
    val skinPresets: StateFlow<Map<String, StyleDefinition>> =
        combine(skin, darkMode) { skinMap, isDark -> skinMap.toPresets(isDark) }
            .stateIn(externalScope, SharingStarted.Eagerly, emptyMap())

    // Highlights, names, and variables live in human-editable TOML files (one per character)
    // rather than the database. The store owns them in memory and persists on every change; a
    // one-time migration copies any existing rows out of SQLite on first launch.
    val characterConfigStore = CharacterConfigStore(warlockDirs.configDir, fileSystem)

    // Client-wide settings and the connection/character registry live in their own TOML files
    // (client.toml, connections.toml), separate from the per-character files above.
    val clientConfigStore = ClientConfigStore(warlockDirs.configDir, fileSystem)

    // Derived data, refetched from the server whenever it is missing, so it lives with the data
    // rather than the configuration.
    val commandListStore = CommandListStore(warlockDirs.dataDir)

    /**
     * Where a failed settings *write* goes, so the app can tell the user their changes are being
     * dropped. The app shell observes [SettingsProblems.current] and shows what is in it; nothing
     * else needs to know that saving can fail. A settings store that cannot be *read* is a different
     * matter and never gets here: it stops the launch (see [SettingsUnreadableException]).
     */
    val settingsProblems = SettingsProblems(warlockDirs.configDir)

    val variableRepository = VariableRepository(characterConfigStore)
    val characterRepository = CharacterRepository(clientConfigStore)
    val windowSettingRepository =
        WindowSettingsRepository(database.windowSettingsDao(), characterConfigStore, settingsProblems)
    val macroRepository =
        MacroRepository(
            database.macroDao(),
            characterConfigStore,
            KeyboardKeyMappings.keyCodeMap,
            KeyboardKeyMappings.reverseKeyCodeMap,
        )
    val accountRepository = AccountRepository(database.accountDao(), settingsProblems)
    val highlightRepository = HighlightRepositoryImpl(characterConfigStore)
    val nameRepository = NameRepositoryImpl(characterConfigStore)
    val presetRepository = PresetRepository(characterConfigStore)
    val progressBarSettingRepository = ProgressBarSettingRepository(characterConfigStore)
    val clientSettings =
        ClientSettingRepository(database.clientSettingDao(), clientConfigStore, warlockDirs, settingsProblems)
    val loggingRepository = LoggingRepository(clientSettings, externalScope)
    val scriptDirRepository =
        ScriptDirRepository(
            scriptDirDao = database.scriptDirDao(),
            warlockDirs = warlockDirs,
            settingsProblems = settingsProblems,
        )
    val characterSettingsRepository =
        CharacterSettingsRepository(
            characterSettingsQueries = database.characterSettingDao(),
            store = characterConfigStore,
            settingsProblems = settingsProblems,
        )
    val connectionRepository =
        ConnectionRepository(
            store = clientConfigStore,
            accountDao = database.accountDao(),
        )
    val connectionSettingsRepository = ConnectionSettingsRepository(clientConfigStore)
    val aliasRepository = AliasRepository(characterConfigStore)
    val actionRepository = ActionRepository(characterConfigStore)
    val alterationRepository = AlterationRepository(characterConfigStore)
    val commandHistoryRepository = CommandHistoryRepository(characterConfigStore, fileSystem, ioDispatcher)

    private val initializeMutex = Mutex()
    private var initialized = false

    // Declared above the init block below, not beside [initialize] where it is used: property
    // initializers and init blocks run in textual order, so a `= null` sitting after the block
    // would race the coroutine it starts and could erase the very exception that stops the launch.
    private var unreadable: SettingsUnreadableException? = null

    init {
        // Kick initialization off eagerly so reactive consumers get data without having to ask;
        // callers that read config synchronously at startup should still call [initialize] to await
        // it. Nothing here throws into [externalScope]: a settings store that could not be read is
        // remembered by [initialize] and handed to whoever calls it next, which is the entry point
        // that can act on it. Letting it out here would only reach the uncaught handler, which is
        // the very thing that made DESKTOP-3X and 3Y useless to the people they happened to.
        externalScope.launch { runCatching { initialize() } }
    }

    /**
     * Loads the config files, runs the one-time DB->TOML migration, and seeds default macros, in that
     * order, then starts watching for external edits. Suspends until that's done. Safe to call more
     * than once (and from multiple places): the work runs exactly once and later calls just await /
     * return. Callers that read config at startup should call this first so they don't observe empty
     * stores before the migration has populated them.
     *
     * Throws [SettingsUnreadableException] -- on this call and every later one -- when the config
     * files could not be read, so the entry point can tell the user and stop rather than come up
     * with empty settings and save them over the real ones.
     */
    suspend fun initialize() {
        initializeMutex.withLock {
            if (!initialized) {
                initialized = true
                // Reading the config files is the fatal step, and *any* failure of it counts, not
                // only the SettingsUnreadableException the stores raise for a file they could
                // identify as damaged. Everything downstream reads absent data as "nothing saved
                // yet", so a load that failed for a reason we did not anticipate would come up empty
                // and then save that over the real settings -- and kotlinx-io is not always specific
                // about why a read failed. Stopping is the safe default; guessing is not.
                runCatching {
                    characterConfigStore.load()
                    clientConfigStore.load()
                }.onFailure { failure ->
                    unreadable =
                        failure as? SettingsUnreadableException
                            ?: SettingsUnreadableException(
                                SettingsProblem.unreadable(
                                    what = "your settings",
                                    reason = failure.describeForUser(),
                                    settingsLocation = warlockDirs.configDir,
                                ),
                            )
                    Logger.e(failure) { "Failed to read the config files" }
                }
                // The rest is best-effort: a migration or a macro seed that fails leaves the user's
                // settings as they were, so it is logged rather than fatal. Skipped entirely when the
                // config could not be read, since the app is about to stop anyway and these would be
                // working from data that is not the user's.
                if (unreadable == null) {
                    runCatching {
                        ConfigMigration(
                            store = characterConfigStore,
                            clientConfigStore = clientConfigStore,
                            characterDao = database.characterDao(),
                            highlightDao = database.highlightDao(),
                            nameDao = database.nameDao(),
                            variableDao = database.variableDao(),
                            aliasDao = database.aliasDao(),
                            alterationDao = database.alterationDao(),
                            presetStyleDao = database.presetStyleDao(),
                            progressBarSettingDao = database.progressBarSettingDao(),
                            windowSettingsDao = database.windowSettingsDao(),
                            characterSettingDao = database.characterSettingDao(),
                            clientSettingDao = database.clientSettingDao(),
                            connectionDao = database.connectionDao(),
                            macroRepository = macroRepository,
                            fileSystem = fileSystem,
                            configDirectory = warlockDirs.configDir,
                        ).migrateIfNeeded()
                        // Seed default global macros on first run and merge in newly added defaults
                        // on upgrade, after migration so any migrated macros win.
                        macroRepository.seedAndMigrateDefaultMacros(clientSettings)
                    }.onFailure {
                        Logger.e(it) { "Failed to initialize config stores" }
                    }
                    // Pick up external edits and writes from other app instances for the app's life.
                    characterConfigStore.startWatching(externalScope)
                    clientConfigStore.startWatching(externalScope)
                }
            }
            // Read under the lock the writer above holds, so every caller sees it however they
            // arrive -- the eager init in the constructor, or the entry point that can act on it.
            unreadable?.let { throw it }
        }
    }

    private val scriptEngineRepository =
        WarlockScriptEngineRepositoryImpl(
            engines =
                listOf(
                    WslEngine(highlightRepository, nameRepository, variableRepository, soundPlayer, fileSystem),
                    LuaEngine(variableRepository, fileSystem),
                ),
            scriptDirRepository = scriptDirRepository,
            fileSystem = fileSystem,
        )

    private val scriptManagerFactory: ScriptManagerFactory =
        ScriptManagerFactoryImpl(
            fileSystem = fileSystem,
            scriptEngineRepository = scriptEngineRepository,
            externalScope = externalScope,
        )

    val wraythImporter =
        WraythImporter(
            highlightRepository = highlightRepository,
            nameRepository = nameRepository,
            macroRepository = macroRepository,
            fileSystem = fileSystem,
        )

    val gameViewModelFactory by lazy {
        GameViewModelFactory(
            macroRepository = macroRepository,
            variableRepository = variableRepository,
            presetRepository = presetRepository,
            characterSettingsRepository = characterSettingsRepository,
            aliasRepository = aliasRepository,
            actionRepository = actionRepository,
            scriptManagerFactory = scriptManagerFactory,
            windowSettingsRepository = windowSettingRepository,
            progressBarSettingRepository = progressBarSettingRepository,
            clientSettingRepository = clientSettings,
            commandHistoryRepository = commandHistoryRepository,
            connectionRepository = connectionRepository,
            ioDispatcher = ioDispatcher,
        )
    }

    val sgeClientFactory: SgeClientFactory =
        object : SgeClientFactory {
            override fun create(): SgeClient = SgeClientImpl(ioDispatcher)
        }

    val warlockClientFactory: WarlockClientFactory =
        object : WarlockClientFactory {
            override fun createClient(
                windowRegistry: WindowRegistry,
                socket: WarlockSocket,
            ): WarlockClient =
                WraythClient(
                    characterRepository = characterRepository,
                    windowRegistry = windowRegistry,
                    fileLogging = loggingRepository,
                    ioDispatcher = ioDispatcher,
                    socket = socket,
                    commandListStore = commandListStore,
                )
        }

    val windowRegistryFactory by lazy {
        WindowRegistryFactory(
            settingRepository = clientSettings,
            soundPlayer = soundPlayer,
            highlightRepository = highlightRepository,
            nameRepository = nameRepository,
            presetRepository = presetRepository,
            characterSettingsRepository = characterSettingsRepository,
            windowSettingsRepository = windowSettingRepository,
            skinPresets = skinPresets,
            alterationRepository = alterationRepository,
        )
    }

    val mudMobileHttpClient: HttpClient by lazy {
        HttpClient(CIO) {
            // The REST API rides this client, and so does the game stream: MUD Mobile's router
            // speaks WebSocket, which is the one transport every platform we target can open.
            install(WebSockets)
            install(HttpTimeout) {
                // The bound on the API calls, so a service that is down or mid-maintenance fails
                // with something to show the user instead of sitting there. CIO defaults to the
                // same 15s, but only the plugin's is a number: the engine's goes unreported, which
                // is what left "request_timeout=unknown ms" in the log of a real outage. It does not
                // touch the game stream either way - both the plugin and the engine exempt WebSocket
                // requests, which is what lets the router hold a connection through its cold boot.
                requestTimeoutMillis = 15_000
            }
        }
    }

    val mudMobileApi by lazy { MudMobileApi(mudMobileHttpClient) }

    // Backs up / syncs the per-character TOML settings to the user's MUD Mobile account.
    val warlockSettingsSync by lazy {
        WarlockSettingsSync(
            api = mudMobileApi,
            configDirectory = warlockDirs.configDir,
            fileSystem = fileSystem,
            tokenProvider = { clientSettings.getMudMobileToken() },
            writeThroughStore = { path, content -> characterConfigStore.writeSectionFile(path, content) },
        )
    }

    val mudMobileConnectUseCase by lazy {
        MudMobileConnectUseCase(
            api = mudMobileApi,
            httpClient = mudMobileHttpClient,
            sgeClientFactory = sgeClientFactory,
            warlockClientFactory = warlockClientFactory,
            windowRegistryFactory = windowRegistryFactory,
            gameViewModelFactory = gameViewModelFactory,
            ioDispatcher = ioDispatcher,
        )
    }

    val mudMobileDiscoverUseCase by lazy {
        MudMobileDiscoverUseCase(
            api = mudMobileApi,
            sgeClientFactory = sgeClientFactory,
        )
    }

    val dashboardViewModelFactory by lazy {
        DashboardViewModelFactory(
            connectionRepository = connectionRepository,
            connectionSettingsRepository = connectionSettingsRepository,
            sgeClientFactory = sgeClientFactory,
            connectToGameUseCase = connectToGameUseCase,
            clientSettingRepository = clientSettings,
            accountRepository = accountRepository,
            mudMobileApi = mudMobileApi,
            mudMobileConnectUseCase = mudMobileConnectUseCase,
            mudMobileDiscoverUseCase = mudMobileDiscoverUseCase,
            warlockSettingsSync = warlockSettingsSync,
        )
    }

    val sgeViewModelFactory by lazy {
        SgeViewModelFactory(
            clientSettingRepository = clientSettings,
            accountRepository = accountRepository,
            connectionRepository = connectionRepository,
            warlockClientFactory = warlockClientFactory,
            sgeClientFactory = sgeClientFactory,
            gameViewModelFactory = gameViewModelFactory,
            windowRegistryFactory = windowRegistryFactory,
            ioDispatcher = ioDispatcher,
        )
    }

    val connectToGameUseCase by lazy {
        ConnectToGameUseCase(
            warlockProxyFactory = warlockProxyFactory,
            windowRegistryFactory = windowRegistryFactory,
            warlockClientFactory = warlockClientFactory,
            gameViewModelFactory = gameViewModelFactory,
            dirs = warlockDirs,
            ioDispatcher = ioDispatcher,
        )
    }

    val settingsTransferUseCase by lazy {
        SettingsTransferUseCase(exportRepository)
    }

    val exportRepository by lazy {
        ExportRepository(
            accountDao = database.accountDao(),
            characterSettingDao = database.characterSettingDao(),
            clientSettingDao = database.clientSettingDao(),
            scriptDirDao = database.scriptDirDao(),
            windowSettingsDao = database.windowSettingsDao(),
            characterConfigStore = characterConfigStore,
            clientConfigStore = clientConfigStore,
        )
    }
}

/**
 * Open the prefs database for the current schema version, applying the versioned-snapshot
 * strategy. Platform entry points call this with a factory that produces a platform-correct
 * [RoomDatabase.Builder] (Android needs a Context, JVM/iOS do not). The legacy single-file
 * name was `prefs.db`; on first launch after this change it is renamed to `warlock-vN.db`.
 *
 * Throws [SettingsUnreadableException] when the database cannot be opened, so the caller can tell
 * the user and stop. Carrying on is not an option worth having: every store treats missing data as
 * "nothing saved yet", so an app that started anyway would come up with empty settings and then
 * write them over a database that was only unreachable, not empty. Issue DESKTOP-3Y is what this
 * replaces -- a user whose volume had gone read-only, so Room could not create the `.lck` file it
 * locks while opening, and the `IllegalStateException` came out of the first query on the main
 * thread before a window ever appeared, telling them nothing.
 *
 * The check is a real query rather than a guess about the filesystem, because Room builds lazily:
 * `build()` does no I/O, and the file is opened, migrated and locked by whatever touches it first.
 * Probing here is what moves that moment somewhere it can be handled and reported.
 */
suspend fun openPrefsDatabase(
    directory: Path,
    fileSystem: FileSystem,
    builderFactory: (databaseFilePath: String) -> RoomDatabase.Builder<PrefsDatabase>,
): PrefsDatabase {
    val sqlDriver = BundledSQLiteDriver()
    // Held so the catch can close a database that was built before the failure. The snapshot work
    // can throw too -- creating the directory, seeding from an older snapshot -- and on the
    // read-only volume this is written for, that is just as fatal as the open itself.
    var built: PrefsDatabase? = null
    try {
        val database =
            openVersionedDatabase(
                directory = directory,
                fileSystem = fileSystem,
                currentVersion = PREFS_DATABASE_VERSION,
                legacyFileName = "prefs.db",
                buildDatabase = { dbPath ->
                    builderFactory(dbPath.toString())
                        .setDriver(sqlDriver)
                        .addMigrations(MIGRATION_10_11, MIGRATION_14_16, MIGRATION_20_21)
                        .build()
                },
                checkpoint = { dbPath -> checkpointDatabase(dbPath, fileSystem, sqlDriver) },
                readSchemaVersion = { dbPath -> readSchemaVersion(dbPath, fileSystem, sqlDriver) },
            ).also { built = it }
        // Any read will do; this one opens the file, runs pending migrations and takes Room's lock.
        database.clientSettingDao().getAll()
        return database
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        snapshotLogger.e(e) { "Could not open the preferences database" }
        runCatching { built?.close() }
        throw SettingsUnreadableException(
            SettingsProblem.unreadable(
                what = "your settings database",
                reason = e.describeForUser(),
                settingsLocation = directory.toString(),
            ),
        )
    }
}

private val snapshotLogger = Logger.withTag("DatabaseSnapshot")

/**
 * Fold a source database's write-ahead log into its main `.db` file (via
 * `PRAGMA wal_checkpoint(TRUNCATE)`) so that copying the main file alone captures all committed
 * data. Best-effort: a non-WAL database makes this a no-op, and any failure is logged rather than
 * aborting startup.
 */
private fun checkpointDatabase(
    path: Path,
    fileSystem: FileSystem,
    sqlDriver: SQLiteDriver,
) {
    if (!fileSystem.exists(path)) return
    runCatching {
        sqlDriver.open(path.toString()).use { connection ->
            connection.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
        }
    }.onFailure { snapshotLogger.w(it) { "Failed to checkpoint ${path.name} before seeding snapshot" } }
}

/**
 * Read a database file's `PRAGMA user_version` -- the schema version Room stamps once it has
 * migrated the file. Best-effort: a missing or unreadable file reports null, which the snapshot
 * machinery reads as "no evidence either way" and leaves the file alone.
 */
private fun readSchemaVersion(
    path: Path,
    fileSystem: FileSystem,
    sqlDriver: SQLiteDriver,
): Int? {
    if (!fileSystem.exists(path)) return null
    return runCatching {
        sqlDriver.open(path.toString()).use { connection ->
            connection.prepare("PRAGMA user_version").use { statement ->
                if (statement.step()) statement.getInt(0) else null
            }
        }
    }.onFailure { snapshotLogger.w(it) { "Failed to read the schema version of ${path.name}" } }
        .getOrNull()
}
