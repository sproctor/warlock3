package warlockfe.warlock3.scripting.lua

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import warlockfe.warlock3.core.client.LineInterceptor

/**
 * Takes the game's answer to a command off the screen for `capture()`: from the first line that
 * matches [start] (or the first line at all, without one) through the line that matches [end], or,
 * without an [end], up to the next prompt. Lines before the start are left alone. A prompt before
 * anything was taken belongs to an earlier command, and is ignored.
 */
internal class CaptureInterceptor(
    private val start: Regex?,
    private val end: Regex?,
) : LineInterceptor {
    private val taken = MutableStateFlow<List<String>>(emptyList())
    private var started = false

    /** Completes when the capture is over. */
    val done = CompletableDeferred<Unit>()

    val lines: List<String> get() = taken.value

    override fun intercept(text: String): Boolean {
        if (done.isCompleted) return false
        if (!started) {
            if (start != null && !start.containsMatchIn(text)) return false
            started = true
        }
        taken.update { it + text }
        if (end?.containsMatchIn(text) == true) done.complete(Unit)
        return true
    }

    override fun prompt() {
        if (started && end == null) done.complete(Unit)
    }
}
