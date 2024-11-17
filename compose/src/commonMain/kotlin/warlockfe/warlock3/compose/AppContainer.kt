package warlockfe.warlock3.compose

import kotlinx.coroutines.CoroutineDispatcher
import warlockfe.warlock3.compose.components.CompassTheme
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
import warlockfe.warlock3.core.prefs.PresetRepository
import warlockfe.warlock3.core.prefs.ScriptDirRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.prefs.sql.Database
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.util.WarlockDirs
import java.io.StringReader
import java.util.*

abstract class AppContainer(
    val database: Database,
    ioDispatcher: CoroutineDispatcher,
    private val themeText: String,
    warlockDirs: WarlockDirs,
) {

    val variableRepository = VariableRepository(database.variableQueries, ioDispatcher)
    val characterRepository =
        CharacterRepository(
            database.characterQueries,
            ioDispatcher
        )
    val macroRepository = MacroRepository(database.macroQueries, ioDispatcher)
    val accountRepository = AccountRepository(database.accountQueries, ioDispatcher)
    val highlightRepository =
        HighlightRepository(
            database.highlightQueries,
            database.highlightStyleQueries,
            ioDispatcher
        )
    val presetRepository = PresetRepository(database.presetStyleQueries, ioDispatcher)
    val clientSettings =
        ClientSettingRepository(
            database.clientSettingQueries,
            ioDispatcher
        )
    val scriptDirRepository =
        ScriptDirRepository(
            scriptDirQueries = database.scriptDirQueries,
            ioDispatcher = ioDispatcher,
            warlockDirs = warlockDirs,
        )
    val characterSettingsRepository =
        CharacterSettingsRepository(
            characterSettingsQueries = database.characterSettingQueries,
            ioDispatcher = ioDispatcher
        )
    val aliasRepository =
        AliasRepository(
            database.aliasQueries,
            ioDispatcher = ioDispatcher
        )
    val alterationRepository =
        AlterationRepository(
            database.alterationQueries,
            ioDispatcher = ioDispatcher
        )
    val themeProperties = Properties().apply {
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