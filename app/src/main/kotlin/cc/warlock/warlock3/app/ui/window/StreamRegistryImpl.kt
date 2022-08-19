package cc.warlock.warlock3.app.ui.window

import cc.warlock.warlock3.core.prefs.CharacterSettingsRepository
import cc.warlock.warlock3.core.window.StreamRegistry
import cc.warlock.warlock3.core.window.TextStream

class StreamRegistryImpl(
    private val characterSettingsRepository: CharacterSettingsRepository
) : StreamRegistry {
    private val streams = HashMap<String, TextStream>()

    @Synchronized
    override fun getOrCreateStream(name: String): TextStream {
        streams[name]?.let { return it }
        return ComposeTextStream(name, 5000)
            .also { streams[name] = it }
    }

    override fun getStreams(): Collection<TextStream> {
        return streams.values
    }
}