package warlockfe.warlock3.core.client

import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * The lines behind [WarlockClient.streamLines]. A client asks [isWatched] before building a line's
 * event, so the streams nobody is collecting cost it nothing: with one script watching "thoughts",
 * every other stream's lines, the debug stream's copy of every line from the server among them,
 * are passed over without being turned into text.
 *
 * Sending never waits: each collector has its own buffer of its own stream's lines, and one that
 * falls behind loses its oldest rather than holding up the game's parse loop, and every window
 * with it. A loss is logged and counted in [linesDropped].
 */
@OptIn(ExperimentalAtomicApi::class)
class StreamLineFeed {
    private val logger = Logger.withTag("StreamLineFeed")

    private class Collector(
        val stream: String,
        val lines: Channel<ClientTextEvent>,
    )

    // Replaced, never changed in place: read on every line by the client, changed by collectors.
    private val collectors = MutableStateFlow<List<Collector>>(emptyList())

    private val sent = AtomicLong(0L)
    private val dropped = AtomicLong(0L)

    /** How many lines have been sent to collectors, for seeing that the unwatched are skipped. */
    val linesSent: Long get() = sent.load()

    /** How many lines collectors lost by falling behind, for seeing why a script missed some. */
    val linesDropped: Long get() = dropped.load()

    /** Whether anyone is collecting [stream]'s lines (null for a connection's one stream, main). */
    fun isWatched(stream: String?): Boolean {
        val watching = collectors.value
        if (watching.isEmpty()) return false
        val id = stream ?: "main"
        return watching.any { it.stream.equals(id, ignoreCase = true) }
    }

    /**
     * Sends [event] to the collectors of its stream, [ClientTextEvent.streamId]; only worth calling
     * once [isWatched] says it has some.
     */
    fun emit(event: ClientTextEvent) {
        sent.incrementAndFetch()
        val id = event.streamId ?: "main"
        for (collector in collectors.value) {
            if (collector.stream.equals(id, ignoreCase = true)) collector.lines.trySend(event)
        }
    }

    /** [stream]'s lines, from the moment collection starts; the stream is named in any case. */
    fun linesOf(stream: String): Flow<ClientTextEvent> =
        flow {
            val collector =
                Collector(
                    stream = stream,
                    lines =
                        Channel(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST) { line ->
                            // Logged on the feed's first loss only, as a collector that falls
                            // behind once usually stays behind a while; [linesDropped] has the rest.
                            if (dropped.incrementAndFetch() == 1L) {
                                logger.w { "A collector of stream \"$stream\" fell behind and lost lines, \"${line.text}\" first" }
                            }
                        },
                )
            // Registered as collection starts, before anything here suspends, so a collector
            // started undispatched is in place by the time its caller goes on.
            collectors.update { it + collector }
            try {
                for (line in collector.lines) emit(line)
            } finally {
                collectors.update { it - collector }
            }
        }
}
