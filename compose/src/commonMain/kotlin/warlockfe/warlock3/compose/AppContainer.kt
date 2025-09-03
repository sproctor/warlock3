package warlockfe.warlock3.compose

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
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
import warlockfe.warlock3.core.prefs.AccountRepository
import warlockfe.warlock3.core.prefs.AliasRepository
import warlockfe.warlock3.core.prefs.AlterationRepository
import warlockfe.warlock3.core.prefs.CharacterRepository
import warlockfe.warlock3.core.prefs.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.ClientSettingRepository
import warlockfe.warlock3.core.prefs.ConnectionRepository
import warlockfe.warlock3.core.prefs.ConnectionSettingsRepository
import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.LoggingRepository
import warlockfe.warlock3.core.prefs.MacroRepository
import warlockfe.warlock3.core.prefs.NameRepository
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.prefs.PresetRepository
import warlockfe.warlock3.core.prefs.ScriptDirRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.core.prefs.WindowRepositoryFactory
import warlockfe.warlock3.core.script.ScriptManagerFactory
import warlockfe.warlock3.core.script.WarlockScriptEngineRepository
import warlockfe.warlock3.core.sge.SgeClient
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.core.window.StreamRegistry
import warlockfe.warlock3.wrayth.network.SgeClientImpl
import warlockfe.warlock3.wrayth.network.WraythClient
import java.io.StringReader
import java.util.*

abstract class AppContainer(
    databaseBuilder: RoomDatabase.Builder<PrefsDatabase>,
    warlockDirs: WarlockDirs,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    val externalScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    val database = databaseBuilder
        .setDriver(BundledSQLiteDriver())
        .addMigrations(
            object : Migration(10, 11) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("DROP TABLE Alteration")
                    connection.execSQL(
                        """
                        CREATE TABLE alteration (
                            id BLOB NOT NULL PRIMARY KEY,
                            characterId TEXT NOT NULL,
                            pattern TEXT NOT NULL,
                            sourceStream TEXT,
                            destinationStream TEXT,
                            result TEXT,
                            ignoreCase INTEGER NOT NULL,
                            keepOriginal INTEGER NOT NULL
                        );
                    """.trimIndent()
                    )
                }
            }
        )
        .build()
    val variableRepository = VariableRepository(database.variableDao())
    val characterRepository =
        CharacterRepository(
            characterDao = database.characterDao(),
        )
    val macroRepository = MacroRepository(database.macroDao())
    val accountRepository = AccountRepository(database.accountDao())
    val highlightRepository = HighlightRepository(database.highlightDao())
    val nameRepository = NameRepository(database.nameDao())
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

    abstract val soundPlayer: SoundPlayer

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
                return SgeClientImpl(Dispatchers.IO)
            }
        }

    val warlockClientFactory: WarlockClientFactory =
        object : WarlockClientFactory {
            override fun createClient(
                windowRepository: WindowRepository,
                streamRegistry: StreamRegistry,
            ): WarlockClient {
                return WraythClient(
                    windowRepository = windowRepository,
                    characterRepository = characterRepository,
                    streamRegistry = streamRegistry,
                    fileLogging = loggingRepository,
                )
            }
        }

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
            ioDispatcher = ioDispatcher,
        )
    }
}