package cc.warlock.warlock3.app.di

import cc.warlock.warlock3.core.prefs.*
import cc.warlock.warlock3.core.prefs.models.PresetRepository
import cc.warlock.warlock3.core.prefs.sql.Database
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
    val windowRepository by lazy { WindowRepository(database.openWindowQueries, Dispatchers.IO) }
}