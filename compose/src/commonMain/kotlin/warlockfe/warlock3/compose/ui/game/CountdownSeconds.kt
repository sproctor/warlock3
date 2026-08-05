package warlockfe.warlock3.compose.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The whole seconds remaining until [endTime], rounded up, ticking on each second boundary and
 * stopping at 0 (or immediately for a null/past [endTime]). Drives the round-time and cast-time
 * counters in the entry bars.
 */
@Composable
internal fun countdownSeconds(
    endTime: Instant?,
    getCurrentTime: () -> Instant,
): Int {
    var seconds by remember { mutableIntStateOf(0) }
    val currentGetCurrentTime by rememberUpdatedState(getCurrentTime)
    LaunchedEffect(endTime) {
        while (endTime != null) {
            val now = currentGetCurrentTime()
            val remaining = endTime - now
            val remainingMs = remaining.inWholeMilliseconds
            seconds = ((remainingMs + 999) / 1000).toInt()
            if (remaining < Duration.ZERO) break
            val msUntilNextTick = remainingMs % 1000
            delay(if (msUntilNextTick == 0L) 1.seconds else msUntilNextTick.milliseconds)
        }
    }
    return seconds
}
