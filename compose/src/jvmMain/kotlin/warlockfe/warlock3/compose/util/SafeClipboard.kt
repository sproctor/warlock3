package warlockfe.warlock3.compose.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A [Clipboard] that survives the system clipboard being unavailable.
 *
 * Windows lets one process hold the clipboard open at a time and AWT does not wait its turn: while
 * something else has it - another app's copy, a clipboard manager's poll, a remote desktop session
 * syncing it - `setContents` throws `IllegalStateException: cannot open system clipboard`. Compose
 * copies from a coroutine with nothing to catch that (`SelectionContainer`, every text field), so
 * it reaches the uncaught handler and takes the app down, which is what DESKTOP-2G was.
 *
 * Whoever holds the lock is normally finishing a clipboard operation of their own, so a retry a
 * moment later usually gets it. A copy that never lands is logged and dropped: the user is no worse
 * off than with the copy that failed, minus the crash.
 */
class SafeClipboard(
    private val delegate: Clipboard,
    private val attempts: Int = 4,
    private val retryDelay: Duration = 50.milliseconds,
) : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? = retrying("read") { delegate.getClipEntry() }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        retrying("write") { delegate.setClipEntry(clipEntry) }
    }

    // Compose's own desktop code still reads this one - the context menu asks the native clipboard
    // whether it holds text - and the interface's default implementation throws.
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override val nativeClipboard: NativeClipboard
        get() = delegate.nativeClipboard

    private suspend fun <T> retrying(
        operation: String,
        block: suspend () -> T,
    ): T? {
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                // On the JVM this is an IllegalStateException, and swallowing it would leave a
                // cancelled coroutine believing it still has work to do.
                throw e
            } catch (e: IllegalStateException) {
                if (attempt == attempts - 1) {
                    logger.w(e) { "Clipboard $operation gave up after $attempts attempts" }
                    return null
                }
                delay(retryDelay)
            } catch (e: Exception) {
                // Not the clipboard being busy, so there is nothing to wait for.
                logger.w(e) { "Clipboard $operation failed" }
                return null
            }
        }
        return null
    }

    private companion object {
        val logger = Logger.withTag("SafeClipboard")
    }
}

/** Wraps [this] unless it already is one, so nested providers don't multiply the retries. */
fun Clipboard.asSafeClipboard(): Clipboard = this as? SafeClipboard ?: SafeClipboard(this)

/** The scene's clipboard, wrapped so a busy system clipboard cannot crash the app. */
@Composable
fun rememberSafeClipboard(): Clipboard {
    val clipboard = LocalClipboard.current
    return remember(clipboard) { clipboard.asSafeClipboard() }
}

/**
 * Runs [content] with a [SafeClipboard] in [LocalClipboard].
 *
 * Every Compose scene provides its own clipboard - a dialog or a detached window is a new scene,
 * and the locals it provides override whatever the parent composition provided - so this belongs
 * inside each window rather than once around the whole app.
 */
@Composable
fun ProvideSafeClipboard(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalClipboard provides rememberSafeClipboard(),
        content = content,
    )
}
