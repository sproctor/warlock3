package warlockfe.warlock3.compose

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import warlockfe.warlock3.compose.components.CompassTheme
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModelFactory
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.compose.ui.sge.SgeViewModelFactory
import warlockfe.warlock3.compose.util.loadCompassTheme
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.prefs.AccountRepository
import warlockfe.warlock3.core.prefs.AliasRepository
import warlockfe.warlock3.core.prefs.AlterationRepository
import warlockfe.warlock3.core.prefs.CharacterRepository
import warlockfe.warlock3.core.prefs.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.ClientSettingRepository
import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.MacroRepository
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.prefs.PresetRepository
import warlockfe.warlock3.core.prefs.ScriptDirRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.util.WarlockDirs
import java.io.StringReader
import java.util.*

abstract class AppContainer(
    databaseBuilder: RoomDatabase.Builder<PrefsDatabase>,
    ioDispatcher: CoroutineDispatcher,
    warlockDirs: WarlockDirs,
) {
    val database = databaseBuilder
        .setDriver(BundledSQLiteDriver())
        .addMigrations(
            object : Migration(10, 11) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("DROP TABLE Alteration")
                    connection.execSQL("""
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
                    """.trimIndent())
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
    val presetRepository = PresetRepository(database.presetStyleDao())
    val clientSettings = ClientSettingRepository(database.clientSettingDao())
    val scriptDirRepository =
        ScriptDirRepository(
            scriptDirDao = database.scriptDirDao(),
            warlockDirs = warlockDirs,
        )
    val characterSettingsRepository =
        CharacterSettingsRepository(
            characterSettingsQueries = database.characterSettingDao(),
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
    abstract val scriptManager: ScriptManager
    val gameViewModelFactory by lazy {
        GameViewModelFactory(
            macroRepository = macroRepository,
            variableRepository = variableRepository,
            scriptManager = scriptManager,
            compassTheme = compassTheme,
            highlightRepository = highlightRepository,
            presetRepository = presetRepository,
            characterSettingsRepository = characterSettingsRepository,
            aliasRepository = aliasRepository,
        )
    }

    abstract val sgeClientFactory: SgeClientFactory
    abstract val warlockClientFactory: WarlockClientFactory

    val dashboardViewModelFactory by lazy {
        DashboardViewModelFactory(
            characterRepository = characterRepository,
            characterSettingsRepository = characterSettingsRepository,
            accountRepository = accountRepository,
            gameViewModelFactory = gameViewModelFactory,
            sgeClientFactory = sgeClientFactory,
            warlockClientFactory = warlockClientFactory,
            ioDispatcher = ioDispatcher,
        )
    }

    val sgeViewModelFactory by lazy {
        SgeViewModelFactory(
            clientSettingRepository = clientSettings,
            accountRepository = accountRepository,
            characterRepository = characterRepository,
            warlockClientFactory = warlockClientFactory,
            sgeClientFactory = sgeClientFactory,
            gameViewModelFactory = gameViewModelFactory,
        )
    }
}