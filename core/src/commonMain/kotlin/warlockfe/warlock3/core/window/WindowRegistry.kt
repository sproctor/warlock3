package warlockfe.warlock3.core.window

import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.core.text.StyleDefinition

interface WindowRegistry {

    val presets: StateFlow<Map<String, StyleDefinition>>

    fun getOrCreateStream(name: String): TextStream

    fun getStreams(): Collection<TextStream>

    fun getOrCreateDialog(name: String): DialogState

    fun setCharacterId(characterId: String)
}
