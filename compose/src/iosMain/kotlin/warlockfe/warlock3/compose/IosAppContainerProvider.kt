package warlockfe.warlock3.compose

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLibraryDirectory
import platform.Foundation.NSUserDomainMask
import warlockfe.warlock3.compose.util.insertDefaultMacrosIfNeeded
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.script.ScriptManagerFactory
import warlockfe.warlock3.core.sge.SgeSettings
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.scripting.ScriptManagerFactoryImpl
import warlockfe.warlock3.scripting.WarlockScriptEngineRepositoryImpl
import warlockfe.warlock3.scripting.wsl.WslEngine

@OptIn(ExperimentalForeignApi::class)
object IosAppContainerProvider {
    val appContainer: AppContainer by lazy {
        val fileManager = NSFileManager.defaultManager
        val documentsUrl = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first()
        val libraryUrl = fileManager.URLsForDirectory(NSLibraryDirectory, NSUserDomainMask).first()

        @Suppress("CAST_NEVER_SUCCEEDS")
        val documentsPath = (documentsUrl as platform.Foundation.NSURL).path!!

        @Suppress("CAST_NEVER_SUCCEEDS")
        val libraryPath = (libraryUrl as platform.Foundation.NSURL).path!!

        val warlockDirs =
            WarlockDirs(
                homeDir = documentsPath,
                configDir = "$libraryPath/Application Support/warlock",
                dataDir = "$libraryPath/Application Support/warlock",
                logDir = "$documentsPath/logs",
            )

        val dbDir = "$libraryPath/Application Support/warlock"
        NSFileManager.defaultManager.createDirectoryAtPath(dbDir, true, null, null)

        val database =
            openPrefsDatabase(
                directory = Path(dbDir),
                fileSystem = SystemFileSystem,
                builderFactory = { filename -> Room.databaseBuilder<PrefsDatabase>(name = filename) },
            )

        val container = IosAppContainer(database, warlockDirs, SystemFileSystem)

        runBlocking {
            container.macroRepository.insertDefaultMacrosIfNeeded()
        }

        container
    }

    val sgeSettings: SgeSettings by lazy {
        val simuCert =
            runBlocking {
                warlockfe.warlock3.compose.generated.resources.Res
                    .readBytes("files/simu.pem")
            }
        SgeSettings(
            host = "eaccess.play.net",
            port = 7910,
            certificate = simuCert,
            secure = true,
        )
    }
}

private class IosSoundPlayer : SoundPlayer {
    override suspend fun playSound(filename: String): String? {
        // TODO: implement iOS sound playback
        return null
    }
}

private class IosProxy(
    command: String,
) : WarlockProxy {
    override val isAlive: Boolean = false
    override val stdOut: Flow<String> = emptyFlow()
    override val stdErr: Flow<String> = emptyFlow()

    override fun close() {}
}

class IosAppContainer(
    database: PrefsDatabase,
    warlockDirs: WarlockDirs,
    fileSystem: FileSystem,
) : AppContainer(
        database = database,
        warlockDirs = warlockDirs,
        fileSystem = fileSystem,
    ) {
    override val soundPlayer: SoundPlayer = IosSoundPlayer()

    override val scriptEngineRepository =
        WarlockScriptEngineRepositoryImpl(
            engines =
                listOf(
                    WslEngine(highlightRepository, nameRepository, variableRepository, soundPlayer, fileSystem),
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

    override val warlockProxyFactory: WarlockProxy.Factory =
        object : WarlockProxy.Factory {
            override fun create(command: String): WarlockProxy = IosProxy(command)
        }
}
