package warlockfe.warlock3.app.di

import androidx.room.RoomDatabase
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.script.ScriptManagerFactory
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.scripting.ScriptManagerFactoryImpl
import warlockfe.warlock3.scripting.WarlockScriptEngineRepositoryImpl
import warlockfe.warlock3.scripting.js.JsEngineFactory
import warlockfe.warlock3.scripting.wsl.WslEngineFactory

class JvmAppContainer(
    databaseBuilder: RoomDatabase.Builder<PrefsDatabase>,
    warlockDirs: WarlockDirs,
) : AppContainer(
    databaseBuilder = databaseBuilder,
    warlockDirs = warlockDirs,
) {
    override val scriptEngineRepository =
        WarlockScriptEngineRepositoryImpl(
            wslEngineFactory = WslEngineFactory(highlightRepository, variableRepository),
            jsEngineFactory = JsEngineFactory(variableRepository),
            scriptDirRepository = scriptDirRepository,
        )
    override val scriptManagerFactory: ScriptManagerFactory =
        ScriptManagerFactoryImpl(
            scriptEngineRepository = scriptEngineRepository,
            externalScope = externalScope,
        )
}