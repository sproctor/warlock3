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
            feed.emit(ClientTextEvent("a goblin says", "main"))
            feed.emit(ClientTextEvent("a goblin thinks", "thoughts"))
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
            feed.emit(ClientTextEvent("A line", null))
            awaitHeard(heard)
            collector.cancelAndJoin()
            assertEquals(listOf(ClientTextEvent("A line", null)), synchronized(heard) { heard.toList() })
        }

    private suspend fun awaitHeard(heard: List<ClientTextEvent>) {
        withTimeout(10.seconds) {
            while (synchronized(heard) { heard.isEmpty() }) yield()
        }
    }
}
