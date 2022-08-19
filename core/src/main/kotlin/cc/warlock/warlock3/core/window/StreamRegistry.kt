package cc.warlock.warlock3.core.window

interface StreamRegistry {
    fun getOrCreateStream(name: String): TextStream

    fun getStreams(): Collection<TextStream>
}