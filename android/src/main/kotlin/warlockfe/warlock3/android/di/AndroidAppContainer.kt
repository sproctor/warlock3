package warlockfe.warlock3.android.di

import androidx.room.RoomDatabase
import kotlinx.io.files.FileSystem
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.core.client.AndroidProxy
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.client.WarlockSocketFactory
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.script.ScriptManagerFactory
import warlockfe.warlock3.core.util.AndroidSoundPlayer
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.scripting.ScriptManagerFactoryImpl
import warlockfe.warlock3.scripting.WarlockScriptEngineRepositoryImpl
import warlockfe.warlock3.scripting.js.JsEngine
import warlockfe.warlock3.scripting.wsl.WslEngine
import warlockfe.warlock3.wrayth.network.JavaSocket
import java.net.Socket

class AndroidAppContainer(
    databaseBuilder: RoomDatabase.Builder<PrefsDatabase>,
    warlockDirs: WarlockDirs,
    fileSystem: FileSystem,
) : AppContainer(
    databaseBuilder = databaseBuilder,
    warlockDirs = warlockDirs,
    fileSystem = fileSystem,
) {

    override val soundPlayer: SoundPlayer = AndroidSoundPlayer()

    override val scriptEngineRepository =
        WarlockScriptEngineRepositoryImpl(
            engines = listOf(
                WslEngine(highlightRepository, nameRepository, variableRepository, soundPlayer, fileSystem),
                JsEngine(variableRepository),
            ),
            scriptDirRepository = scriptDirRepository,
            fileSystem = fileSystem,
        )
    override val scriptManagerFactory: ScriptManagerFactory =
        ScriptManagerFactoryImpl(
            fileSystem = fileSystem,
            scriptEngineRepository = scriptEngineRepository,
            externalScope = externalScope,
        )

    override val warlockSocketFactory: WarlockSocketFactory =
        object : WarlockSocketFactory {
            override fun create(host: String, port: Int): WarlockSocket {
                return JavaSocket(Socket(host, port))
            }
        }

    override val warlockProxyFactory: WarlockProxy.Factory =
        object : WarlockProxy.Factory {
            override fun create(command: String): WarlockProxy {
                return AndroidProxy(command)
            }
        }
}
