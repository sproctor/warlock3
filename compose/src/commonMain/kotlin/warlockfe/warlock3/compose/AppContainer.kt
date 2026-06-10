package warlockfe.warlock3.compose

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import warlockfe.warlock3.compose.macros.KeyboardKeyMappings
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModelFactory
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.compose.ui.sge.SgeViewModelFactory
import warlockfe.warlock3.compose.ui.window.WindowRegistryFactory
import warlockfe.warlock3.compose.util.toPresets
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.prefs.MIGRATION_10_11
import warlockfe.warlock3.core.prefs.MIGRATION_14_16
import warlockfe.warlock3.core.prefs.MySQLiteDriver
import warlockfe.warlock3.core.prefs.PREFS_DATABASE_VERSION
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.ConfigMigration
import warlockfe.warlock3.core.prefs.repositories.AccountRepository
import warlockfe.warlock3.core.prefs.repositories.AliasRepository
import warlockfe.warlock3.core.prefs.repositories.AlterationRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
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
import warlockfe.warlock3.core.script.WarlockScriptEngineRepository
import warlockfe.warlock3.core.sge.SgeClient
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.core.window.WindowRegistry
import warlockfe.warlock3.wrayth.network.SgeClientImpl
import warlockfe.warlock3.wrayth.network.WraythClient
import warlockfe.warlock3.wrayth.settings.WraythImporter

abstract class AppContainer(
    val database: PrefsDatabase,
    warlockDirs: WarlockDirs,
    fileSystem: FileSystem,
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

    val variableRepository = VariableRepository(characterConfigStore)
    val characterRepository =
        CharacterRepository(
            characterDao = database.characterDao(),
        )
    val windowSettingRepository = WindowSettingsRepository(database.windowSettingsDao())
    val macroRepository =
        MacroRepository(
            database.macroDao(),
            KeyboardKeyMappings.keyCodeMap,
            KeyboardKeyMappings.reverseKeyCodeMap,
        )
    val accountRepository = AccountRepository(database.accountDao())
    val highlightRepository = HighlightRepositoryImpl(characterConfigStore)
    val nameRepository = NameRepositoryImpl(characterConfigStore)
    val presetRepository = PresetRepository(database.presetStyleDao())
    val progressBarSettingRepository = ProgressBarSettingRepository(database.progressBarSettingDao())
    val clientSettings = ClientSettingRepository(database.clientSettingDao(), warlockDirs)
    val loggingRepository = LoggingRepository(clientSettings, externalScope)
    val scriptDirRepository =
        ScriptDirRepository(
            scriptDirDao = database.scriptDirDao(),
            warlockDirs = warlockDirs,
        )
    val characterSettingsRepository =
        CharacterSettingsRepository(
            characterSettingsQueries = database.characterSettingDao(),
        )
    val connectionRepository =
        ConnectionRepository(
            connectionDao = database.connectionDao(),
        )
    val connectionSettingsRepository =
        ConnectionSettingsRepository(
            connectionSettingDao = database.connectionSettingDao(),
        )
    val aliasRepository =
        AliasRepository(
            database.aliasDao(),
        )
    val alterationRepository =
        AlterationRepository(
            database.alterationDao(),
        )

    init {
        externalScope.launch {
            characterConfigStore.load()
            ConfigMigration(
                store = characterConfigStore,
                characterDao = database.characterDao(),
                highlightDao = database.highlightDao(),
                nameDao = database.nameDao(),
                variableDao = database.variableDao(),
                fileSystem = fileSystem,
                configDirectory = warlockDirs.configDir,
            ).migrateIfNeeded()
            // Pick up external edits and writes from other app instances for the app's lifetime.
            characterConfigStore.startWatching(externalScope)
        }
    }

    abstract val scriptEngineRepository: WarlockScriptEngineRepository
    abstract val scriptManagerFactory: ScriptManagerFactory

    abstract val soundPlayer: SoundPlayer

    val wraythImporter =
        WraythImporter(
            highlightRepository = highlightRepository,
            nameRepository = nameRepository,
            macroDao = database.macroDao(),
            fileSystem = fileSystem,
        )

    val gameViewModelFactory by lazy {
        GameViewModelFactory(
            macroRepository = macroRepository,
            variableRepository = variableRepository,
            presetRepository = presetRepository,
            characterSettingsRepository = characterSettingsRepository,
            aliasRepository = aliasRepository,
            scriptManagerFactory = scriptManagerFactory,
            windowSettingsRepository = windowSettingRepository,
            progressBarSettingRepository = progressBarSettingRepository,
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
                )
        }

    abstract val warlockProxyFactory: WarlockProxy.Factory

    val windowRegistryFactory by lazy {
        WindowRegistryFactory(
            settingRepository = clientSettings,
            soundPlayer = soundPlayer,
            highlightRepository = highlightRepository,
            nameRepository = nameRepository,
            presetRepository = presetRepository,
            skinPresets = skinPresets,
            alterationRepository = alterationRepository,
        )
    }

    val dashboardViewModelFactory by lazy {
        DashboardViewModelFactory(
            connectionRepository = connectionRepository,
            connectionSettingsRepository = connectionSettingsRepository,
            sgeClientFactory = sgeClientFactory,
            connectToGameUseCase = connectToGameUseCase,
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
            aliasDao = database.aliasDao(),
            alterationDao = database.alterationDao(),
            characterDao = database.characterDao(),
            characterSettingDao = database.characterSettingDao(),
            clientSettingDao = database.clientSettingDao(),
            connectionDao = database.connectionDao(),
            connectionSettingDao = database.connectionSettingDao(),
            macroDao = database.macroDao(),
            presetStyleDao = database.presetStyleDao(),
            scriptDirDao = database.scriptDirDao(),
            windowSettingsDao = database.windowSettingsDao(),
            characterConfigStore = characterConfigStore,
        )
    }
}

/**
 * Open the prefs database for the current schema version, applying the versioned-snapshot
 * strategy. Platform entry points call this with a factory that produces a platform-correct
 * [RoomDatabase.Builder] (Android needs a Context, JVM/iOS do not). The legacy single-file
 * name was `prefs.db`; on first launch after this change it is renamed to `warlock-vN.db`.
 */
fun openPrefsDatabase(
    directory: Path,
    fileSystem: FileSystem,
    builderFactory: (databaseFilePath: String) -> RoomDatabase.Builder<PrefsDatabase>,
): PrefsDatabase =
    openVersionedDatabase(
        directory = directory,
        fileSystem = fileSystem,
        currentVersion = PREFS_DATABASE_VERSION,
        legacyFileName = "prefs.db",
        buildDatabase = { dbPath ->
            builderFactory(dbPath.toString())
                .setDriver(MySQLiteDriver(BundledSQLiteDriver()))
                .addMigrations(MIGRATION_10_11, MIGRATION_14_16)
                .build()
        },
        checkpoint = { dbPath -> checkpointDatabase(dbPath, fileSystem) },
    )

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
) {
    if (!fileSystem.exists(path)) return
    runCatching {
        val connection = MySQLiteDriver(BundledSQLiteDriver()).open(path.toString())
        try {
            connection.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
        } finally {
            connection.close()
        }
    }.onFailure { snapshotLogger.w(it) { "Failed to checkpoint ${path.name} before seeding snapshot" } }
}
