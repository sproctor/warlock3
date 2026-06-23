package warlockfe.warlock3.compose.ui.window

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Lightweight instrumentation for diagnosing multi-window / multi-connection lag. Disabled by
 * default; flip [enabled] to true to turn it on.
 *
 * When enabled, [StreamWorkQueue] records, per connection, how long each op waited in the queue
 * before running (queue backlog), how long it took to run (render cost), the peak queue depth, and a
 * per-op-type breakdown. A summary line is logged every [reportInterval], and any op that waited
 * longer than [stallThreshold] is logged immediately with a wall-clock timestamp (so a stall can be
 * lined up against a GC/safepoint pause in the JVM log). All of this is under the "StreamWorkQueue"
 * log tag.
 */
object StreamProfiling {
    var enabled: Boolean = false

    val reportInterval = 5.seconds

    // An op whose queue-wait exceeds this is logged immediately with a wall-clock timestamp, so the
    // stall can be lined up against a GC/safepoint pause in the JVM log. ~15ms is roughly one dropped
    // frame, well above normal sub-millisecond waits.
    val stallThreshold = 15.milliseconds
}
