package warlockfe.warlock3.core.window

interface StreamRegistry {
    suspend fun getOrCreateStream(name: String): TextStream

    fun getStreams(): Collection<TextStream>

    fun setCharacterId(characterId: String)
}