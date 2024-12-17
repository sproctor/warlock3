package warlockfe.warlock3.compose.ui.window

import warlockfe.warlock3.core.window.StreamRegistry
import warlockfe.warlock3.core.window.TextStream
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.synchronized

class StreamRegistryImpl(
    private val ioDispatcher: CoroutineDispatcher,
) : StreamRegistry {
    private var streams = persistentHashMapOf<String, TextStream>()

    override fun getOrCreateStream(name: String): TextStream {
        synchronized(this) {
            streams[name]?.let { return it }
            return ComposeTextStream(name, 5000, ioDispatcher)
                .also { streams = streams.put(name, it) }
        }
    }

    override fun getStreams(): Collection<TextStream> {
        return streams.values
    }
}