package cc.warlock.warlock3.app.di

import androidx.compose.ui.platform.ClipboardManager
import cc.warlock.warlock3.app.GameState
import cc.warlock.warlock3.app.components.CompassTheme
import cc.warlock.warlock3.app.ui.dashboard.DashboardViewModel
import cc.warlock.warlock3.app.ui.game.GameViewModel
import cc.warlock.warlock3.app.util.loadCompassTheme
import cc.warlock.warlock3.core.prefs.*
import cc.warlock.warlock3.core.prefs.sql.Database
import cc.warlock.warlock3.core.script.WarlockScriptEngineRegistry
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.Dispatchers

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
    val scriptEngineRegistry by lazy {
        WarlockScriptEngineRegistry(
            highlightRepository = highlightRepository,
            variableRepository = variableRepository,
            clientSettingRepository = clientSettings,
        )
    }
    val characterSettingsRepository by lazy {
        CharacterSettingsRepository(
            characterSettingsQueries = database.characterSettingQueries,
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