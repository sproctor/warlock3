package warlockfe.warlock3.compose.ui.window

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

// During a sustained burst the channel never drains, so cap how many ops run between coalesced
// flushes to bound how stale the displayed lines can get. At a few tens of microseconds per op this
// is well under one frame, while still collapsing a deep burst's hundreds of publishes into a few.
private const val MAX_OPS_BETWEEN_FLUSHES = 256

@OptIn(ExperimentalAtomicApi::class, ExperimentalTime::class)
class StreamWorkQueue(
    scope: CoroutineScope,
    capacity: Int = 1000,
) {
    private val logger = Logger.withTag("StreamWorkQueue")

    // Identifies which connection/character this queue belongs to in profiling output. Written from
    // the registry's setCharacterId on a different coroutine than the consumer that reads it, so it is
    // @Volatile for safe publication; it only labels diagnostic output.
    @Volatile
    var tag: String = "?"

    // Something with coalesced output to publish once a drain batch finishes (a ComposeTextStream).
    fun interface Flushable {
        fun flush()
    }

    private val channel =
        Channel<suspend () -> Unit>(
            capacity = capacity,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )

    // Submitted-but-not-yet-started op count, maintained only while profiling is enabled (it feeds the
    // queue-depth metric). It is an upper bound during backpressure: a producer increments before its
    // channel.send() may suspend, so a blocked producer is counted before its op is actually enqueued.
    private val pending = AtomicInt(0)

    // Streams that produced output during the current drain batch and barriers waiting for it to be
    // published. Both are only touched from the single consumer coroutine, so need no synchronization.
    private val pendingFlushes = LinkedHashSet<Flushable>()
    private val pendingBarriers = ArrayDeque<CompletableDeferred<Unit>>()

    // Only touched by the single consumer coroutine below, so no synchronization is needed.
    private val stats = ProfileStats()

    init {
        val consumer =
            scope.launch(Dispatchers.Default) {
                for (firstOp in channel) {
                    runOp(firstOp)
                    // Greedily run whatever else is already queued without publishing between ops, so a
                    // burst's many line updates collapse into a single published snapshot per stream.
                    var sinceFlush = 1
                    while (true) {
                        val next = channel.tryReceive().getOrNull() ?: break
                        runOp(next)
                        if (++sinceFlush >= MAX_OPS_BETWEEN_FLUSHES) {
                            flushAll()
                            sinceFlush = 0
                        }
                    }
                    // Channel drained: publish the coalesced output, then release anyone awaiting it.
                    flushAll()
                    completeBarriers()
                }
            }
        // If the consumer stops (its scope is cancelled) while barriers are still pending, fail their
        // awaiters instead of leaving awaitFlushed() suspended forever.
        consumer.invokeOnCompletion { cause ->
            while (pendingBarriers.isNotEmpty()) {
                pendingBarriers.removeFirst().completeExceptionally(
                    cause ?: CancellationException("StreamWorkQueue stopped"),
                )
            }
        }
    }

    private suspend fun runOp(op: suspend () -> Unit) {
        try {
            op()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.e(e) { "Stream op threw" }
        }
    }

    // Request that [flushable] publish its coalesced output once the current drain batch finishes.
    // Called from within an op, i.e. on the consumer coroutine.
    fun scheduleFlush(flushable: Flushable) {
        pendingFlushes.add(flushable)
    }

    private fun flushAll() {
        if (pendingFlushes.isEmpty()) return
        for (flushable in pendingFlushes) {
            try {
                flushable.flush()
            } catch (e: Throwable) {
                logger.e(e) { "Stream flush threw" }
            }
        }
        pendingFlushes.clear()
    }

    private fun completeBarriers() {
        while (pendingBarriers.isNotEmpty()) {
            pendingBarriers.removeFirst().complete(Unit)
        }
    }

    suspend fun submit(
        label: String = "?",
        op: suspend () -> Unit,
    ) {
        // Default path (profiling disabled): enqueue the op directly. No clock read, no atomic, and no
        // wrapper allocation, so the work queue adds nothing measurable to the per-op hot path.
        if (!StreamProfiling.enabled) {
            channel.send(op)
            return
        }
        // Profiling enabled: wrap the op so the consumer records its queue-wait, exec time, and depth.
        val submittedAt = TimeSource.Monotonic.markNow()
        pending.incrementAndFetch()
        channel.send {
            val depth = pending.decrementAndFetch()
            val waited = submittedAt.elapsedNow()
            if (waited >= StreamProfiling.stallThreshold) {
                // The stall happened in roughly [now - waited, now]; match that window against a
                // GC/safepoint pause in the JVM log.
                logger.w {
                    "[$tag] STALL: '$label' waited $waited (queueDepth=$depth) ending ${Clock.System.now()}"
                }
            }
            val execMark = TimeSource.Monotonic.markNow()
            op()
            stats.record(label = label, waited = waited, exec = execMark.elapsedNow(), depth = depth)
            stats.maybeReport(logger, tag)
        }
    }

    // Suspend until everything submitted so far has run and its coalesced output has been published.
    // The barrier op registers a completion that the consumer fires only after the post-drain flush,
    // so on return the displayed [lines] reflect all prior submits. Intended for tests.
    suspend fun awaitFlushed() {
        val done = CompletableDeferred<Unit>()
        submit("await-flush") { pendingBarriers.add(done) }
        done.await()
    }
}

// Accumulates work-queue timings and periodically logs a summary. Single-consumer-thread only.
private class ProfileStats {
    private var windowStart = TimeSource.Monotonic.markNow()
    private var count = 0
    private var sumWait = Duration.ZERO
    private var maxWait = Duration.ZERO
    private var sumExec = Duration.ZERO
    private var maxExec = Duration.ZERO
    private var maxDepth = 0
    private val countByLabel = HashMap<String, Int>()
    private val execByLabel = HashMap<String, Duration>()

    fun record(
        label: String,
        waited: Duration,
        exec: Duration,
        depth: Int,
    ) {
        count++
        sumWait += waited
        if (waited > maxWait) maxWait = waited
        sumExec += exec
        if (exec > maxExec) maxExec = exec
        if (depth > maxDepth) maxDepth = depth
        countByLabel[label] = (countByLabel[label] ?: 0) + 1
        execByLabel[label] = (execByLabel[label] ?: Duration.ZERO) + exec
    }

    fun maybeReport(
        logger: Logger,
        tag: String,
    ) {
        val elapsed = windowStart.elapsedNow()
        if (elapsed < StreamProfiling.reportInterval) return
        if (count > 0) {
            val breakdown =
                execByLabel.entries
                    .sortedByDescending { it.value }
                    .joinToString(", ") { (label, total) -> "$label x${countByLabel[label]}=$total" }
            logger.i {
                "[$tag] $count ops in $elapsed | queue-wait avg=${sumWait / count} max=$maxWait" +
                    " | exec avg=${sumExec / count} max=$maxExec | peakDepth=$maxDepth | $breakdown"
            }
        }
        reset()
    }

    private fun reset() {
        windowStart = TimeSource.Monotonic.markNow()
        count = 0
        sumWait = Duration.ZERO
        maxWait = Duration.ZERO
        sumExec = Duration.ZERO
        maxExec = Duration.ZERO
        maxDepth = 0
        countByLabel.clear()
        execByLabel.clear()
    }
}
