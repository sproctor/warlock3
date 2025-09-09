package warlockfe.warlock3.app.di

import androidx.room.RoomDatabase
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.client.WarlockSocketFactory
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.script.ScriptManagerFactory
import warlockfe.warlock3.core.util.DesktopSoundPlayer
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.scripting.ScriptManagerFactoryImpl
import warlockfe.warlock3.scripting.WarlockScriptEngineRepositoryImpl
import warlockfe.warlock3.scripting.js.JsEngineFactory
import warlockfe.warlock3.scripting.wsl.WslEngineFactory
import warlockfe.warlock3.wrayth.network.JavaSocket
import java.net.Socket

class JvmAppContainer(
    databaseBuilder: RoomDatabase.Builder<PrefsDatabase>,
    warlockDirs: WarlockDirs,
) : AppContainer(
    databaseBuilder = databaseBuilder,
    warlockDirs = warlockDirs,
) {
    override val soundPlayer: SoundPlayer = DesktopSoundPlayer(warlockDirs)

    override val scriptEngineRepository =
        WarlockScriptEngineRepositoryImpl(
            wslEngineFactory = WslEngineFactory(highlightRepository, nameRepository, variableRepository, soundPlayer),
            jsEngineFactory = JsEngineFactory(variableRepository),
            scriptDirRepository = scriptDirRepository,
        )
    override val scriptManagerFactory: ScriptManagerFactory =
        ScriptManagerFactoryImpl(
            scriptEngineRepository = scriptEngineRepository,
            externalScope = externalScope,
        )

    override val warlockSocketFactory: WarlockSocketFactory =
        object : WarlockSocketFactory {
            override fun create(host: String, port: Int): WarlockSocket {
                return JavaSocket(Socket(host, port))
            }
        }
}
