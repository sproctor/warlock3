package warlockfe.warlock3.core.client

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update

/**
 * The lines behind [WarlockClient.streamLines]. A client asks [isWatched] before building a line's
 * event, so the streams nobody is collecting cost it nothing: with one script watching "thoughts",
 * every other stream's lines, the debug stream's copy of every line from the server among them,
 * are passed over without being turned into text.
 */
class StreamLineFeed {
    private val lines = MutableSharedFlow<ClientTextEvent>(extraBufferCapacity = 256)

    // How many collectors each stream has, by lowercased name.
    private val watchers = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Whether anyone is collecting [stream]'s lines (null for a connection's one stream, main). */
    fun isWatched(stream: String?): Boolean {
        val watched = watchers.value
        if (watched.isEmpty()) return false
        val id = stream ?: "main"
        return watched.keys.any { it.equals(id, ignoreCase = true) }
    }

    /** Sends [event] to [stream]'s collectors; only worth calling once [isWatched] says it has some. */
    suspend fun emit(event: ClientTextEvent) {
        lines.emit(event)
    }

    fun linesOf(stream: String): Flow<ClientTextEvent> {
        val id = stream.lowercase()
        return flow {
            // Counted once subscribed, so a collector cancelled before then is not uncounted.
            var counted = false
            try {
                lines
                    .onSubscription {
                        watchers.update { it + (id to (it[id] ?: 0) + 1) }
                        counted = true
                    }.collect { line ->
                        if ((line.streamId ?: "main").equals(id, ignoreCase = true)) emit(line)
                    }
            } finally {
                if (counted) {
                    watchers.update {
                        val count = (it[id] ?: 1) - 1
                        if (count == 0) it - id else it + (id to count)
                    }
                }
            }
        }
    }
}
