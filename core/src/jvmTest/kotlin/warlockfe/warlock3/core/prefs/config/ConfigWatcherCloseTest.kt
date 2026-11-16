package warlockfe.warlock3.core.prefs.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * A config watcher holds one of the user's inotify instances (on Linux; a limited resource, 128 by
 * default), so it has to give it back when it is cancelled - including when the dispatcher its
 * collector ran on will never run anything again, as a finished test's does. A watcher that only
 * closed once its collector was dispatched again leaked one per test container, until unrelated
 * tests failed to open any.
 */
@OptIn(ExperimentalPathApi::class)
class ConfigWatcherCloseTest {
    private val dir = Files.createTempDirectory("config-watcher-close-test")

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** Runs what is dispatched to it only when [runQueued] is called, like a test's scheduler. */
    private class ManualDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            synchronized(queue) { queue.addLast(block) }
        }

        fun runQueued() {
            while (true) {
                val next = synchronized(queue) { queue.removeFirstOrNull() } ?: return
                next.run()
            }
        }
    }

    // The inotify instances this process holds, or null where /proc does not show them (not Linux).
    private fun inotifyInstances(): Int? =
        File("/proc/self/fd").listFiles()?.count { fd ->
            runCatching { Files.readSymbolicLink(fd.toPath()).toString() == "anon_inode:inotify" }.getOrDefault(false)
        }

    private suspend fun waitFor(condition: () -> Boolean): Boolean =
        withTimeoutOrNull(5.seconds) {
            while (!condition()) delay(10)
            true
        } ?: false

    @Test
    fun cancellingReleasesTheWatchEvenWhenItsDispatcherNeverRunsAgain() =
        runBlocking {
            val before = inotifyInstances() ?: return@runBlocking
            val dispatcher = ManualDispatcher()
            val job = Job()
            CoroutineScope(dispatcher + job).launch {
                watchConfigChanges(dir.toString()).collect {}
            }
            dispatcher.runQueued()
            check(waitFor { inotifyInstances() == before + 1 }) { "the watcher never opened" }

            // From here on the dispatcher never runs anything, so the collector is never resumed.
            job.cancel()

            assertTrue(waitFor { inotifyInstances() == before }, "the watch was still open after its job was cancelled")
        }
}
