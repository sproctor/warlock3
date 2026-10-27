import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.client.StreamLineFeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class StreamLineFeedTest {
    @Test
    fun aStreamIsWatchedOnlyWhileSomethingCollectsIt() =
        runBlocking {
            val feed = StreamLineFeed()
            assertFalse(feed.isWatched("thoughts"))
            val heard = mutableListOf<ClientTextEvent>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    feed.linesOf("Thoughts").collect { synchronized(heard) { heard += it } }
                }
            // Watched as soon as the collector is subscribed, in any case, and only that stream.
            assertTrue(feed.isWatched("thoughts"))
            assertFalse(feed.isWatched("main"))
            assertFalse(feed.isWatched(null))
            // Main's first, so it has been passed over by the time the thoughts line is heard.
            feed.send(ClientTextEvent("a goblin says", "main"))
            feed.send(ClientTextEvent("a goblin thinks", "thoughts"))
            awaitHeard(heard)
            collector.cancelAndJoin()
            assertFalse(feed.isWatched("thoughts"))
            assertEquals(listOf(ClientTextEvent("a goblin thinks", "thoughts")), synchronized(heard) { heard.toList() })
        }

    @Test
    fun aConnectionsUnnamedStreamIsMain() =
        runBlocking {
            val feed = StreamLineFeed()
            val heard = mutableListOf<ClientTextEvent>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    feed.linesOf("main").collect { synchronized(heard) { heard += it } }
                }
            assertTrue(feed.isWatched(null))
            feed.send(ClientTextEvent("A line", null))
            awaitHeard(heard)
            collector.cancelAndJoin()
            assertEquals(listOf(ClientTextEvent("A line", null)), synchronized(heard) { heard.toList() })
        }

    private suspend fun awaitHeard(heard: List<ClientTextEvent>) {
        withTimeout(10.seconds) {
            while (synchronized(heard) { heard.isEmpty() }) yield()
        }
    }

    @Test
    fun aCollectorThatFallsBehindLosesItsOldestLinesAndTheSenderGoesOn() =
        runBlocking {
            val feed = StreamLineFeed()
            val heard = mutableListOf<ClientTextEvent>()
            // Runs only when this thread is free: until then, every line waits in its buffer.
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    feed.linesOf("thoughts").collect { synchronized(heard) { heard += it } }
                }
            // Sent without waiting on the collector, which cannot run until they all are.
            repeat(300) { feed.send(ClientTextEvent("line $it", "thoughts")) }
            withTimeout(10.seconds) {
                while (synchronized(heard) { heard.lastOrNull()?.text } != "line 299") yield()
            }
            collector.cancelAndJoin()
            // Some lost, in one stretch, and the newest all heard, in order.
            val lines = synchronized(heard) { heard.map { it.text.removePrefix("line ").toInt() } }
            assertTrue(lines.size < 300)
            assertEquals(299, lines.last())
            assertTrue(lines.zipWithNext().all { (a, b) -> b > a })
            assertEquals(1, lines.zipWithNext().count { (a, b) -> b != a + 1 })
        }

    @Test
    fun aCollectorsLossesAreLogged() {
        val warnings = mutableListOf<String>()
        val saved = Logger.config.logWriterList
        Logger.setLogWriters(
            object : LogWriter() {
                override fun log(
                    severity: Severity,
                    message: String,
                    tag: String,
                    throwable: Throwable?,
                ) {
                    if (severity == Severity.Warn && tag == "StreamLineFeed") synchronized(warnings) { warnings += message }
                }
            },
        )
        try {
            runBlocking {
                val feed = StreamLineFeed()
                // Never reads a line, so all but a buffer's worth are lost.
                val collector = launch(start = CoroutineStart.UNDISPATCHED) { feed.linesOf("thoughts").collect { awaitCancellation() } }
                repeat(300) { feed.send(ClientTextEvent("line $it", "thoughts")) }
                collector.cancelAndJoin()
            }
        } finally {
            Logger.setLogWriters(saved)
        }
        val logged = synchronized(warnings) { warnings.toList() }
        // The first loss as it happened, then the total when the collector stopped.
        assertEquals(2, logged.size, logged.toString())
        assertTrue(logged[0].contains("is losing lines"), logged[0])
        assertTrue(Regex("lost \\d+ lines in all").containsMatchIn(logged[1]), logged[1])
    }
}
