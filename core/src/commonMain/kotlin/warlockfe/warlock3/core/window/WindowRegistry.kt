package warlockfe.warlock3.core.window

import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString

interface WindowRegistry {
    val presets: StateFlow<Map<String, StyleDefinition>>

    fun getOrCreateStream(name: String): TextStream

    fun getStreams(): Collection<TextStream>

    // Apply a server component update (vitals, room objects, hands, etc.) to every window that
    // displays it, as a single batched operation.
    suspend fun updateComponent(
        name: String,
        value: StyledString,
    )

    fun getOrCreateDialog(name: String): DialogState

    fun setCharacterId(characterId: String)

    fun close()
}
