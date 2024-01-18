package warlockfe.warlock3.app.di

import androidx.compose.ui.platform.ClipboardManager
import warlockfe.warlock3.app.GameState
import warlockfe.warlock3.app.components.CompassTheme
import warlockfe.warlock3.app.ui.dashboard.DashboardViewModel
import warlockfe.warlock3.app.ui.game.GameViewModel
import warlockfe.warlock3.app.ui.window.StreamRegistryImpl
import warlockfe.warlock3.app.util.loadCompassTheme
import warlockfe.warlock3.core.prefs.sql.Database
import warlockfe.warlock3.core.script.WarlockScriptEngineRegistry
import warlockfe.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.Dispatchers
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

object AppContainer {

    lateinit var database: Database

    val variableRepository by lazy { VariableRepository(database.variableQueries, Dispatchers.IO) }
    val characterRepository by lazy { CharacterRepository(database.characterQueries, Dispatchers.IO) }
    val macroRepository by lazy { MacroRepository(database.macroQueries, Dispatchers.IO) }
    val accountRepository by lazy { AccountRepository(database.accountQueries, Dispatchers.IO) }
    val highlightRepository by lazy {
        HighlightRepository(database.highlightQueries, database.highlightStyleQueries, Dispatchers.IO)
    }
    val presetRepository by lazy { PresetRepository(database.presetStyleQueries, Dispatchers.IO) }
    val clientSettings by lazy { ClientSettingRepository(database.clientSettingQueries, Dispatchers.IO) }
    val windowRepository by lazy { WindowRepository(database.windowSettingsQueries, Dispatchers.IO) }
    val scriptDirRepository by lazy { ScriptDirRepository(database.scriptDirQueries, Dispatchers.IO) }
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
    val compassTheme: CompassTheme by lazy { loadCompassTheme() }
    val gameViewModelFactory = { client: StormfrontClient, clipboard: ClipboardManager ->
        GameViewModel(
            client = client,
            windowRepository = windowRepository,
            macroRepository = macroRepository,
            variableRepository = variableRepository,
            scriptEngineRegistry = scriptEngineRegistry,
            compassTheme = compassTheme,
            clipboard = clipboard,
            highlightRepository = highlightRepository,
            presetRepository = presetRepository,
            characterSettingsRepository = characterSettingsRepository,
            aliasRepository = aliasRepository,
            streamRegistry = streamRegistry,
        )
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
        )
    }
}