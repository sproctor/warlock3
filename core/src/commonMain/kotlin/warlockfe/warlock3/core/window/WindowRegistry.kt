package warlockfe.warlock3.core.window

import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.core.text.ResolvedStyle
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockColor

interface WindowRegistry {
    // The named style presets as sparse layers (no "default" — the base style is separate, see
    // [baseStyle]); sparse so per-item fonts + tri-state background reach the renderer.
    val presets: StateFlow<Map<String, StyleLayer>>

    // The resolved base ("default text") style — colors + font + weight + italic/underline, cascaded
    // skin -> global -> character. What un-styled game text and the input/status chrome render as.
    val baseStyle: StateFlow<ResolvedStyle>

    // The skin's named-color palette (slot -> color), so a window's skin-referenced color can be
    // resolved by callers that build window styles outside the registry (GameViewModel).
    val colorPalette: StateFlow<Map<String, WarlockColor>>

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
