package warlockfe.warlock3.compose

import androidx.room3.Room
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.compose.crash.CrashReporter
import warlockfe.warlock3.compose.crash.CrashReporting
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.core.util.WarlockDirs
import java.nio.file.FileSystemException
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * A real [AppContainer] with all its files under [dir]: no sound, no crash reports, and no proxy
 * unless the test brings its own [warlockProxyFactory].
 */
suspend fun testAppContainer(
    dir: java.nio.file.Path,
    ioDispatcher: CoroutineDispatcher,
    warlockProxyFactory: WarlockProxy.Factory = WarlockProxy.Factory { throw UnsupportedOperationException() },
): AppContainer {
    val database =
        openPrefsDatabase(
            directory = Path(dir.toString()),
            fileSystem = SystemFileSystem,
            builderFactory = { filename -> Room.databaseBuilder<PrefsDatabase>(name = filename) },
        )
    return AppContainer(
        database = database,
        warlockDirs =
            WarlockDirs(
                homeDir = dir.toString(),
                dataDir = dir.resolve("data").toString(),
                configDir = dir.toString(),
                logDir = dir.resolve("logs").toString(),
            ),
        fileSystem = SystemFileSystem,
        soundPlayer =
            object : SoundPlayer {
                override suspend fun playSound(filename: String): String? = null
            },
        warlockProxyFactory = warlockProxyFactory,
        crashReporting =
            CrashReporting(
                configDir = dir.toString(),
                dataDir = dir.resolve("data").toString(),
                fileSystem = SystemFileSystem,
                reporter =
                    object : CrashReporter {
                        override fun start(onReport: (eventId: String, json: String) -> Unit) = Unit

                        override fun stop() = Unit
                    },
                background = Dispatchers.Unconfined,
            ),
        ioDispatcher = ioDispatcher,
    )
}

/**
 * Deletes a test's [dir]. Work the test started on the IO dispatcher (a game's log, a settings
 * write) can still be adding files as it goes, so a delete that finds the folder refilled is tried
 * again for a little while.
 */
@OptIn(ExperimentalPathApi::class)
fun deleteTestDir(dir: java.nio.file.Path) {
    repeat(20) {
        try {
            dir.deleteRecursively()
            return
        } catch (_: FileSystemException) {
            Thread.sleep(50)
        }
    }
    dir.deleteRecursively()
}
