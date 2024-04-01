package warlockfe.warlock3.app.di

import ca.gosyer.appdirs.AppDirs
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toPath
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.resources.MR
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.prefs.sql.Database
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.sge.SgeClient
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.scripting.WarlockScriptEngineRegistry
import warlockfe.warlock3.stormfront.network.SgeClientImpl
import warlockfe.warlock3.stormfront.network.StormfrontClient

class JvmAppContainer(
    database: Database,
    appDirs: AppDirs,
) : AppContainer(
    database = database,
    ioDispatcher = Dispatchers.IO,
    themeText = MR.files.theme.readText(),
    appDirs = appDirs,
) {
    override val scriptManager: ScriptManager =
        WarlockScriptEngineRegistry(
            highlightRepository = highlightRepository,
            variableRepository = variableRepository,
            scriptDirRepository = scriptDirRepository,
        )
    override val sgeClientFactory: SgeClientFactory =
        object : SgeClientFactory {
            override fun create(host: String, port: Int): SgeClient {
                return SgeClientImpl(host, port)
            }
        }
    override val warlockClientFactory: WarlockClientFactory =
        object : WarlockClientFactory {
            override fun createStormFrontClient(credentials: SimuGameCredentials): WarlockClient {
                return StormfrontClient(
                    host = credentials.host,
                    port = credentials.port,
                    key = credentials.key,
                    windowRepository = windowRepository,
                    characterRepository = characterRepository,
                    scriptManager = scriptManager,
                    alterationRepository = alterationRepository,
                    streamRegistry = streamRegistry,
                    logPath = appDirs.getUserLogDir().toPath()
                )
            }
        }
}
