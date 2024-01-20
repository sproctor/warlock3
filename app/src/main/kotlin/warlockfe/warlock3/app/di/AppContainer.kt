package warlockfe.warlock3.app.di

import androidx.compose.ui.platform.ClipboardManager
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toPath
import warlockfe.warlock3.compose.components.CompassTheme
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.resources.MR
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModel
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.compose.ui.sge.SgeViewModelFactory
import warlockfe.warlock3.compose.ui.window.StreamRegistryImpl
import warlockfe.warlock3.compose.util.loadCompassTheme
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.prefs.AccountRepository
import warlockfe.warlock3.core.prefs.AliasRepository
import warlockfe.warlock3.core.prefs.AlterationRepository
import warlockfe.warlock3.core.prefs.CharacterRepository
import warlockfe.warlock3.core.prefs.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.ClientSettingRepository
import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.MacroRepository
import warlockfe.warlock3.core.prefs.PresetRepository
import warlockfe.warlock3.core.prefs.ScriptDirRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.core.prefs.sql.Database
import warlockfe.warlock3.core.sge.SgeClient
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.scripting.WarlockScriptEngineRegistry
import warlockfe.warlock3.stormfront.network.SgeClientImpl
import warlockfe.warlock3.stormfront.network.StormfrontClient
import java.io.StringReader
import java.util.Properties

class AppContainer(val database: Database) {

    val variableRepository by lazy { VariableRepository(database.variableQueries, Dispatchers.IO) }
    val characterRepository by lazy {
        CharacterRepository(
            database.characterQueries,
            Dispatchers.IO
        )
    }
    val macroRepository by lazy { MacroRepository(database.macroQueries, Dispatchers.IO) }
    val accountRepository by lazy { AccountRepository(database.accountQueries, Dispatchers.IO) }
    val highlightRepository by lazy {
        HighlightRepository(
            database.highlightQueries,
            database.highlightStyleQueries,
            Dispatchers.IO
        )
    }
    val presetRepository by lazy { PresetRepository(database.presetStyleQueries, Dispatchers.IO) }
    val clientSettings by lazy {
        ClientSettingRepository(
            database.clientSettingQueries,
            Dispatchers.IO
        )
    }
    val windowRepository by lazy {
        WindowRepository(
            database.windowSettingsQueries,
            Dispatchers.IO
        )
    }
    val scriptDirRepository by lazy {
        ScriptDirRepository(
            database.scriptDirQueries,
            Dispatchers.IO
        )
    }
    val scriptEngineRegistry by lazy {
        WarlockScriptEngineRegistry(
            highlightRepository = highlightRepository,
            variableRepository = variableRepository,
            scriptDirRepository = scriptDirRepository,
        )
    }
    val characterSettingsRepository by lazy {
        CharacterSettingsRepository(
            characterSettingsQueries = database.characterSettingQueries,
            ioDispatcher = Dispatchers.IO
        )
    }
    val streamRegistry by lazy { StreamRegistryImpl() }
    val aliasRepository by lazy {
        AliasRepository(
            database.aliasQueries,
            ioDispatcher = Dispatchers.IO
        )
    }
    val alterationRepository by lazy {
        AlterationRepository(
            database.alterationQueries,
            ioDispatcher = Dispatchers.IO
        )
    }
    val compassTheme: CompassTheme by lazy {
        val properties = Properties()
        val themeText = MR.files.theme.readText()
        properties.load(StringReader(themeText))
        loadCompassTheme(properties)
    }
    val gameViewModelFactory =
        GameViewModelFactory(
            windowRepository = windowRepository,
            macroRepository = macroRepository,
            variableRepository = variableRepository,
            scriptManager = scriptEngineRegistry,
            compassTheme = compassTheme,
            highlightRepository = highlightRepository,
            presetRepository = presetRepository,
            characterSettingsRepository = characterSettingsRepository,
            aliasRepository = aliasRepository,
            streamRegistry = streamRegistry,
        )

    val sgeClientFactory = object : SgeClientFactory {
        override fun create(host: String, port: Int): SgeClient {
            return SgeClientImpl(host, port)
        }
    }
    val warlockClientFactory = object : WarlockClientFactory {
        override fun createStormFrontClient(credentials: SimuGameCredentials): WarlockClient {
            return StormfrontClient(
                host = credentials.host,
                port = credentials.port,
                key = credentials.key,
                windowRepository = windowRepository,
                characterRepository = characterRepository,
                scriptManager = scriptEngineRegistry,
                alterationRepository = alterationRepository,
                streamRegistry = streamRegistry,
                logPath = System.getProperty("WARLOCK_LOG_DIR").toPath()
            )
        }
    }

    fun dashboardViewModelFactory(
        updateGameState: (GameState) -> Unit,
        clipboardManager: ClipboardManager,
    ): DashboardViewModel {
        return DashboardViewModel(
            characterRepository = characterRepository,
            accountRepository = accountRepository,
            updateGameState = updateGameState,
            clipboardManager = clipboardManager,
            gameViewModelFactory = gameViewModelFactory,
            sgeClientFactory = sgeClientFactory,
            warlockClientFactory = warlockClientFactory,
            ioDispatcher = Dispatchers.IO,
        )
    }

    val sgeViewModelFactory = SgeViewModelFactory(
        clientSettingRepository = clientSettings,
        accountRepository = accountRepository,
        characterRepository = characterRepository,
        warlockClientFactory = warlockClientFactory,
        sgeClientFactory = sgeClientFactory,
        gameViewModelFactory = gameViewModelFactory,
    )
}