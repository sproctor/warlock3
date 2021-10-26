package cc.warlock.warlock3.app.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import cc.warlock.warlock3.core.highlights.Highlight
import cc.warlock.warlock3.core.highlights.HighlightRegistry
import cc.warlock.warlock3.core.window.Window
import cc.warlock.warlock3.core.window.WindowRegistry
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

class WindowViewModel(
    val name: String,
    client: StormfrontClient,
    val window: Flow<Window?>,
    val highlightRegistry: HighlightRegistry,
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _backgroundColor = mutableStateOf(Color.DarkGray)
    val backgroundColor: State<Color> = _backgroundColor

    private val _textColor = mutableStateOf(Color.White)
    val textColor: State<Color> = _textColor

    val components = client.components

    val lines = client.getStream(name).lines

    val highlights = combine(client.characterId, highlightRegistry.globalHighlights, highlightRegistry.characterHighlights) { characterId, globalHighlights, characterHighlights ->
        globalHighlights + (characterHighlights[characterId?.lowercase()] ?: emptyList())
    }
}