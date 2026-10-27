import kotlinx.coroutines.CoroutineStart
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
}
