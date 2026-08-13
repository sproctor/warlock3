package warlockfe.warlock3.compose.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LatestValueWriterTest {
    @Test
    fun aSlowWriteDoesNotLandAfterANewerOne() =
        runTest {
            val stored = mutableListOf<String>()
            // The first write blocks until we let it go, which is the shape that inverts a pair of
            // plain concurrent writes: the older one is still in the store when the newer finishes.
            val firstWriteReached = CompletableDeferred<Unit>()
            val releaseFirstWrite = CompletableDeferred<Unit>()
            val writer =
                LatestValueWriter<String> { value ->
                    if (stored.isEmpty()) {
                        firstWriteReached.complete(Unit)
                        releaseFirstWrite.await()
                    }
                    stored += value
                }

            val old = launch { writer.write("old") }
            firstWriteReached.await()
            val new = launch { writer.write("new") }
            releaseFirstWrite.complete(Unit)
            old.join()
            new.join()

            assertEquals("new", stored.last(), "the newest layout must be the last one stored")
        }

    @Test
    fun aFlushQueuedBehindAWriteStillStoresTheNewestValue() =
        runTest {
            val stored = mutableListOf<String>()
            val firstWriteReached = CompletableDeferred<Unit>()
            val releaseFirstWrite = CompletableDeferred<Unit>()
            val writer =
                LatestValueWriter<String> { value ->
                    if (stored.isEmpty()) {
                        firstWriteReached.complete(Unit)
                        releaseFirstWrite.await()
                    }
                    stored += value
                }

            // A save is in flight, the user changes the layout again, and the app is closing: the
            // close path's flush must not write the value the in-flight save was carrying.
            val inFlight = launch { writer.write("old") }
            firstWriteReached.await()
            val closing =
                launch {
                    writer.write("new")
                    writer.flush()
                }
            releaseFirstWrite.complete(Unit)
            inFlight.join()
            closing.join()

            assertEquals("new", stored.last())
        }

    @Test
    fun aFlushWithNothingRecordedWritesNothing() =
        runTest {
            val stored = mutableListOf<String>()
            LatestValueWriter<String> { stored += it }.flush()
            assertEquals(emptyList(), stored)
        }
}
