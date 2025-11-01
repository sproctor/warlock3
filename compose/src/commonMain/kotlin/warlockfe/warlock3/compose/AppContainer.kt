package warlockfe.warlock3.compose

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.FileSystem
import org.jetbrains.compose.resources.ExperimentalResourceApi
import warlockfe.warlock3.compose.components.CompassTheme
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModelFactory
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.compose.ui.sge.SgeViewModelFactory
import warlockfe.warlock3.compose.ui.window.StreamRegistryFactory
import warlockfe.warlock3.compose.util.loadCompassTheme
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.client.WarlockSocketFactory
import warlockfe.warlock3.core.prefs.MIGRATION_10_11
import warlockfe.warlock3.core.prefs.MIGRATION_14_16
import warlockfe.warlock3.core.prefs.MySQLiteDriver
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.prefs.repositories.AccountRepository
import warlockfe.warlock3.core.prefs.repositories.AliasRepository
import warlockfe.warlock3.core.prefs.repositories.AlterationRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.LoggingRepository
import warlockfe.warlock3.core.prefs.repositories.MacroRepository
import warlockfe.warlock3.core.prefs.repositories.NameRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.prefs.repositories.ScriptDirRepository
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.prefs.repositories.WindowRepository
import warlockfe.warlock3.core.prefs.repositories.WindowRepositoryFactory
import warlockfe.warlock3.core.script.ScriptManagerFactory
import warlockfe.warlock3.core.script.WarlockScriptEngineRepository
import warlockfe.warlock3.core.sge.SgeClient
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.core.window.StreamRegistry
import warlockfe.warlock3.wrayth.network.SgeClientImpl
import warlockfe.warlock3.wrayth.network.WraythClient
import warlockfe.warlock3.wrayth.settings.WraythImporter
import java.io.StringReader
import java.util.*

abstract class AppContainer(
    databaseBuilder: RoomDatabase.Builder<PrefsDatabase>,
    warlockDirs: WarlockDirs,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    fileSystem: FileSystem,
) {
    val externalScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    val database = databaseBuilder
        .setDriver(MySQLiteDriver(BundledSQLiteDriver()))
        .addMigrations(
            MIGRATION_10_11, MIGRATION_14_16
        )
        .build()
    val variableRepository = VariableRepository(database.variableDao())
    val characterRepository =
        CharacterRepository(
            characterDao = database.characterDao(),
        )
    val macroRepository = MacroRepository(database.macroDao())
    val accountRepository = AccountRepository(database.accountDao())
    val highlightRepository = HighlightRepositoryImpl(database.highlightDao())
    val nameRepository = NameRepositoryImpl(database.nameDao())
    val presetRepository = PresetRepository(database.presetStyleDao())
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
    val connectionRepository = ConnectionRepository(
        connectionDao = database.connectionDao(),
    )
    val connectionSettingsRepository = ConnectionSettingsRepository(
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

    @OptIn(ExperimentalResourceApi::class)
    val themeProperties = Properties().apply {
        val themeText = runBlocking { Res.readBytes("files/theme.properties").decodeToString() }
        load(StringReader(themeText))
    }
    val compassTheme: CompassTheme = loadCompassTheme(themeProperties)
    abstract val scriptEngineRepository: WarlockScriptEngineRepository
    abstract val scriptManagerFactory: ScriptManagerFactory

    abstract val warlockSocketFactory: WarlockSocketFactory

    abstract val soundPlayer: SoundPlayer

    val wraythImporter = WraythImporter(
        highlightRepository = highlightRepository,
        nameRepository = nameRepository,
        fileSystem = fileSystem,
    )

    val gameViewModelFactory by lazy {
        GameViewModelFactory(
            macroRepository = macroRepository,
            variableRepository = variableRepository,
            compassTheme = compassTheme,
            highlightRepository = highlightRepository,
            nameRepository = nameRepository,
            presetRepository = presetRepository,
            characterSettingsRepository = characterSettingsRepository,
            aliasRepository = aliasRepository,
            scriptManagerFactory = scriptManagerFactory,
            alterationRepository = alterationRepository,
            mainDispatcher = mainDispatcher,
        )
    }

    val sgeClientFactory: SgeClientFactory =
        object : SgeClientFactory {
            override fun create(): SgeClient {
                return SgeClientImpl(ioDispatcher)
            }
        }

    val warlockClientFactory: WarlockClientFactory =
        object : WarlockClientFactory {
            override fun createClient(
                windowRepository: WindowRepository,
                streamRegistry: StreamRegistry,
                socket: WarlockSocket,
            ): WarlockClient {
                return WraythClient(
                    windowRepository = windowRepository,
                    characterRepository = characterRepository,
                    streamRegistry = streamRegistry,
                    fileLogging = loggingRepository,
                    ioDispatcher = ioDispatcher,
                    socket = socket,
                )
            }
        }

    abstract val warlockProxyFactory: WarlockProxy.Factory

    val windowRepositoryFactory by lazy {
        WindowRepositoryFactory(
            windowSettingsDao = database.windowSettingsDao(),
            externalScope = externalScope,
        )
    }

    val streamRegistryFactory by lazy {
        StreamRegistryFactory(
            mainDispatcher = mainDispatcher,
            externalScope = externalScope,
            settingRepository = clientSettings,
            ioDispatcher = ioDispatcher,
            soundPlayer = soundPlayer,
            highlightRepository = highlightRepository,
            nameRepository = nameRepository,
            presetRepository = presetRepository,
            alterationRepository = alterationRepository,
        )
    }

    val dashboardViewModelFactory by lazy {
        DashboardViewModelFactory(
            connectionRepository = connectionRepository,
            connectionSettingsRepository = connectionSettingsRepository,
            gameViewModelFactory = gameViewModelFactory,
            sgeClientFactory = sgeClientFactory,
            warlockClientFactory = warlockClientFactory,
            windowRepositoryFactory = windowRepositoryFactory,
            streamRegistryFactory = streamRegistryFactory,
            warlockSocketFactory = warlockSocketFactory,
            warlockProxyFactory = warlockProxyFactory,
            dirs = warlockDirs,
            ioDispatcher = ioDispatcher,
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
            windowRepositoryFactory = windowRepositoryFactory,
            streamRegistryFactory = streamRegistryFactory,
            warlockSocketFactory = warlockSocketFactory,
            ioDispatcher = ioDispatcher,
        )
    }
}