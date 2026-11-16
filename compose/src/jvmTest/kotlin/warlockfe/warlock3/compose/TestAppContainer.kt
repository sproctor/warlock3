package warlockfe.warlock3.compose

import androidx.room3.Room
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.compose.crash.CrashReporter
import warlockfe.warlock3.compose.crash.CrashReporting
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.core.util.WarlockDirs
import java.nio.file.FileSystemException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.time.Duration.Companion.seconds

// The containers each test directory holds, which [deleteTestDir] stops, with the job it cancels
// for each: its own, or null when the test passed one and cancels it itself.
private class OpenContainer(
    val container: AppContainer,
    val ownJob: Job?,
)

private val openContainers = ConcurrentHashMap<java.nio.file.Path, MutableList<OpenContainer>>()

/**
 * A real [AppContainer] with all its files under [dir]: no sound, no crash reports, and no proxy
 * unless the test brings its own [warlockProxyFactory].
 *
 * Its background work runs under [parentJob], which the test then cancels, or else under a job of
 * its own that [deleteTestDir] cancels. [deleteTestDir] also stops its window registries either way. Either stops its config-file watchers: left running, each
 * holds one of the user's inotify instances until the test JVM exits, and enough of them fail every
 * later test that opens a container.
 */
suspend fun testAppContainer(
    dir: java.nio.file.Path,
    ioDispatcher: CoroutineDispatcher,
    warlockProxyFactory: WarlockProxy.Factory = WarlockProxy.Factory { throw UnsupportedOperationException() },
    parentJob: Job? = null,
): AppContainer {
    val ownJob = if (parentJob == null) Job() else null
    val job = parentJob ?: ownJob!!
    return AppContainer
        .open(
            platform =
                AppPlatform(
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
                    parentJob = job,
                ),
            databaseDirectory = Path(dir.toString()),
            databaseBuilder = { filename -> Room.databaseBuilder<PrefsDatabase>(name = filename) },
        ).also { container ->
            openContainers.computeIfAbsent(dir.toAbsolutePath()) { CopyOnWriteArrayList() } += OpenContainer(container, ownJob)
        }
}

/**
 * Deletes a test's [dir]. Work the test started on the IO dispatcher (a game's log, a settings
 * write) can still be adding files as it goes, so a delete that finds the folder refilled is tried
 * again for a little while.
 */
@OptIn(ExperimentalPathApi::class)
fun deleteTestDir(dir: java.nio.file.Path) {
    // Stop the work of every container opened in it first, so nothing is left watching the folder.
    // Its window registries' work is also waited for: it queries the database, which fails once the
    // database is deleted, and kotlinx-coroutines-test reports that as an uncaught exception in some
    // later test. (The rest of a container's work can be queued on a test's dispatcher, which never
    // runs again, so waiting for all of it would only wait out the timeout.)
    val root = dir.toAbsolutePath()
    val containers =
        openContainers.keys
            .filter { it.startsWith(root) }
            .flatMap { key -> openContainers.remove(key).orEmpty() }
    containers.forEach { it.ownJob?.cancel() }
    // A registry that never stops would bring that failure back in some unrelated test, so fail
    // in the test that left it running instead, once the folder is gone.
    val stopped =
        runBlocking {
            withTimeoutOrNull(5.seconds) {
                containers.forEach { it.container.windowRegistryFactory.cancelAndJoinAll() }
            }
        }
    deleteWithRetries(dir)
    checkNotNull(stopped) { "A window registry opened under $dir was still running 5s after it was cancelled" }
}

@OptIn(ExperimentalPathApi::class)
private fun deleteWithRetries(dir: java.nio.file.Path) {
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
