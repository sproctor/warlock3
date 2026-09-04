package warlockfe.warlock3.compose.util

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The crash this guards against (DESKTOP-2G): AWT throws
 * `IllegalStateException: cannot open system clipboard` whenever another process has the Windows
 * clipboard open, and Compose's copy handlers run in a coroutine that nothing catches, so a copy
 * during that moment reached the uncaught handler and killed the app.
 */
class SafeClipboardTest {
    @Test
    fun aBusyClipboardIsRetriedUntilItLetsGo() =
        runTest {
            val fake = FakeClipboard(failures = 2)
            val entry = clipEntryOf("copy me")!!

            SafeClipboard(fake).setClipEntry(entry)

            assertEquals(3, fake.writes)
            assertSame(entry, fake.entry)
        }

    @Test
    fun aClipboardThatNeverFreesUpIsDroppedRatherThanThrown() =
        runTest {
            val fake = FakeClipboard(failures = Int.MAX_VALUE)

            SafeClipboard(fake, attempts = 3).setClipEntry(clipEntryOf("copy me"))

            assertEquals(3, fake.writes)
            assertNull(fake.entry)
        }

    @Test
    fun aWriteThatLandsIsReportedToCallersThatAsk() =
        runTest {
            val fake = FakeClipboard(failures = 2)

            // What {cut} checks before it deletes the text it copied, and what the memory dialog
            // says it did.
            assertTrue(SafeClipboard(fake).trySetClipEntry(clipEntryOf("copy me")))
        }

    @Test
    fun aDroppedWriteIsReportedToCallersThatAsk() =
        runTest {
            val fake = FakeClipboard(failures = Int.MAX_VALUE)

            assertFalse(SafeClipboard(fake, attempts = 2).trySetClipEntry(clipEntryOf("copy me")))
        }

    @Test
    fun aBusyClipboardIsRetriedOnTheWayBackToo() =
        runTest {
            val fake = FakeClipboard(failures = 1).apply { entry = clipEntryOf("paste me") }

            val read = SafeClipboard(fake).getClipEntry()

            assertEquals(2, fake.reads)
            assertEquals("paste me", read?.plainText())
        }

    @Test
    fun aReadThatNeverGetsThroughYieldsNothing() =
        runTest {
            val fake = FakeClipboard(failures = Int.MAX_VALUE).apply { entry = clipEntryOf("paste me") }

            assertNull(SafeClipboard(fake, attempts = 2).getClipEntry())
            assertEquals(2, fake.reads)
        }

    @Test
    fun cancellationIsNotMistakenForABusyClipboard() =
        runTest {
            // java.util.concurrent.CancellationException *is* an IllegalStateException, so a
            // cancelled copy would otherwise be retried and then swallowed.
            val fake = FakeClipboard(failures = Int.MAX_VALUE, failure = { CancellationException("cancelled") })

            assertFailsWith<CancellationException> { SafeClipboard(fake).setClipEntry(clipEntryOf("copy me")) }
            assertEquals(1, fake.writes)
        }

    @Test
    fun aFailureThatIsNotTheClipboardLockIsNotWaitedOut() =
        runTest {
            val fake = FakeClipboard(failures = Int.MAX_VALUE, failure = { IOException("gone") })

            SafeClipboard(fake).setClipEntry(clipEntryOf("copy me"))

            assertEquals(1, fake.writes)
        }

    @Test
    fun wrappingAnAlreadySafeClipboardChangesNothing() {
        val safe = FakeClipboard(failures = 0).asSafeClipboard()

        assertSame(safe, safe.asSafeClipboard())
    }

    @Suppress("DEPRECATION")
    @Test
    fun theNativeClipboardIsTheDelegatesOwn() {
        val fake = FakeClipboard(failures = 0)

        // Compose's desktop context menu reads this to ask whether there is text to paste; the
        // interface's default implementation throws.
        assertSame(fake.nativeClipboard, fake.asSafeClipboard().nativeClipboard)
    }

    /**
     * The production path, end to end: Compose's own copy handler, reached by the key event, with a
     * clipboard that is busy the first two times it is asked.
     *
     * This is what pins the fix in place - the copy is Compose's, not ours, and the only way in is
     * the [LocalClipboard] the container reads. Without [ProvideSafeClipboard] around it the
     * `IllegalStateException` lands in the scene's exception handler, which is where the app had
     * nothing at all.
     */
    @OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
    @Test
    fun aSelectionContainerCopySurvivesABusyClipboard() {
        val composeThread = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "compose-test") }
        try {
            // Written from the compose thread's two handlers, read from this one.
            val failures = CopyOnWriteArrayList<Throwable>()
            val copied = CountDownLatch(1)
            val clipboard = FakeClipboard(failures = 2, onWrite = { copied.countDown() })
            val state = SelectionState()
            val dispatcher = composeThread.asCoroutineDispatcher()

            fun <T> call(block: () -> T): T = composeThread.submit(Callable(block)).get()

            val scene =
                call {
                    // Where the app's crash surfaced, and where a coroutine's failure goes when
                    // nothing in its context takes it.
                    Thread.currentThread().setUncaughtExceptionHandler { _, t -> failures += t }
                    ImageComposeScene(
                        width = 200,
                        height = 100,
                        density = Density(1f),
                        coroutineContext = dispatcher + CoroutineExceptionHandler { _, t -> failures += t },
                    )
                }
            try {
                call {
                    scene.setContent {
                        // Standing in for the window's platform clipboard, which is the one the
                        // scene provides and the one that throws.
                        CompositionLocalProvider(LocalClipboard provides clipboard) {
                            ProvideSafeClipboard {
                                SelectionContainer(state = state) {
                                    BasicText("copy me")
                                }
                            }
                        }
                    }
                    scene.render()
                }
                // Selecting also takes focus, which is what puts the key event in front of the
                // container's copy handler.
                call {
                    state.selectAll()
                    scene.render()
                }
                val handled = call { scene.sendKeyEvent(KeyEvent(Key.Copy, KeyEventType.KeyDown)) }

                assertTrue(handled, "the selection container handled the copy key")
                assertTrue(copied.await(5, TimeUnit.SECONDS), "the copy landed on the clipboard")
                assertEquals(3, call { clipboard.writes })
                assertEquals("copy me", call { clipboard.entry?.plainText() })
                assertTrue(failures.isEmpty(), "no failure escaped: $failures")
            } finally {
                call { scene.close() }
            }
        } finally {
            composeThread.shutdown()
        }
    }

    /** Busy for its first [failures] calls, in the way the Windows clipboard is. */
    private class FakeClipboard(
        private val failures: Int,
        private val failure: () -> Exception = { IllegalStateException("cannot open system clipboard") },
        private val onWrite: () -> Unit = {},
    ) : Clipboard {
        var entry: ClipEntry? = null
        var reads = 0
        var writes = 0
        val native = Any()

        override suspend fun getClipEntry(): ClipEntry? {
            reads++
            if (reads <= failures) throw failure()
            return entry
        }

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            writes++
            if (writes <= failures) throw failure()
            entry = clipEntry
            onWrite()
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override val nativeClipboard: NativeClipboard
            get() = native
    }
}
