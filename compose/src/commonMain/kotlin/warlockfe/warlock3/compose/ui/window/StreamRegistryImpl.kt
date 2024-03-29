package warlockfe.warlock3.compose.ui.window

import warlockfe.warlock3.core.window.StreamRegistry
import warlockfe.warlock3.core.window.TextStream
import kotlinx.collections.immutable.persistentHashMapOf

class StreamRegistryImpl : StreamRegistry {
    private var streams = persistentHashMapOf<String, TextStream>()

    @Synchronized
    override fun getOrCreateStream(name: String): TextStream {
        streams[name]?.let { return it }
        return ComposeTextStream(name, 5000)
            .also { streams = streams.put(name, it) }
    }

    override fun getStreams(): Collection<TextStream> {
        return streams.values
    }
}