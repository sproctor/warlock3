package warlockfe.warlock3.core.window

interface StreamRegistry {
    fun getOrCreateStream(name: String): TextStream

    fun getStreams(): Collection<TextStream>

    fun getOrCreateDialog(name: String): DialogState

    fun setCharacterId(characterId: String)
}