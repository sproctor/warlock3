package warlockfe.warlock3.android.di

import android.content.Context
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toOkioPath
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.core.script.ScriptManagerFactory
import warlockfe.warlock3.core.sge.SgeClient
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.core.window.StreamRegistry
import warlockfe.warlock3.scripting.ScriptManagerFactoryImpl
import warlockfe.warlock3.scripting.WarlockScriptEngineRepositoryImpl
import warlockfe.warlock3.scripting.js.JsEngineFactory
import warlockfe.warlock3.scripting.wsl.WslEngineFactory
import warlockfe.warlock3.stormfront.network.SgeClientImpl
import warlockfe.warlock3.stormfront.network.StormfrontClient

class AndroidAppContainer(
    context: Context,
    databaseBuilder: RoomDatabase.Builder<PrefsDatabase>,
    warlockDirs: WarlockDirs,
) : AppContainer(
    databaseBuilder = databaseBuilder,
    warlockDirs = warlockDirs,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val scriptEngineRepository =
        WarlockScriptEngineRepositoryImpl(
            wslEngineFactory = WslEngineFactory(highlightRepository, variableRepository),
            jsEngineFactory = JsEngineFactory(variableRepository),
            scriptDirRepository = scriptDirRepository,
        )
    override val scriptManagerFactory: ScriptManagerFactory =
        ScriptManagerFactoryImpl(
            scriptEngineRepository = scriptEngineRepository,
            externalScope = scope,
        )

    override val sgeClientFactory = object : SgeClientFactory {
        override fun create(host: String, port: Int): SgeClient {
            return SgeClientImpl(host, port, Dispatchers.IO)
        }
    }
    override val warlockClientFactory = object : WarlockClientFactory {
        override fun createStormFrontClient(
            credentials: SimuGameCredentials,
            windowRepository: WindowRepository,
            streamRegistry: StreamRegistry,
        ): WarlockClient {
            return StormfrontClient(
                host = credentials.host,
                port = credentials.port,
                key = credentials.key,
                windowRepository = windowRepository,
                characterRepository = characterRepository,
                alterationRepository = alterationRepository,
                streamRegistry = streamRegistry,
                logPath = context.filesDir.toOkioPath() / "logs"
            )
        }
    }
}
