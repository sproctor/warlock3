package cc.warlock.warlock3.app.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import cc.warlock.warlock3.app.model.ViewHighlight
import cc.warlock.warlock3.app.util.toSpanStyle
import cc.warlock.warlock3.core.highlights.HighlightRegistry
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.text.StyleRepository
import cc.warlock.warlock3.core.window.Window
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class WindowViewModel(
    val name: String,
    client: StormfrontClient,
    val window: Flow<Window?>,
    val highlightRegistry: HighlightRegistry,
    private val styleRepository: StyleRepository,
) {

    val components = client.components

    val lines = client.getStream(name).lines

    val highlights = combine(
        client.characterId,
        highlightRegistry.globalHighlights,
        highlightRegistry.characterHighlights,
    ) { characterId, globalHighlights, characterHighlights ->
        val settingsHighlights = globalHighlights + (characterHighlights[characterId?.lowercase()] ?: emptyList())
        settingsHighlights.map { highlight ->
            val pattern = if (highlight.isRegex) {
                highlight.pattern
            } else {
                val subpattern = Regex.escape(highlight.pattern)
                if (highlight.matchPartialWord) {
                    subpattern
                } else {
                    "\\b$subpattern\\b"
                }
            }
            ViewHighlight(
                regex = Regex(
                    pattern = pattern,
                    options = if (highlight.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet(),
                ),
                styles = highlight.styles.map { it.toSpanStyle() },
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val styleMap: Flow<Map<String, StyleDefinition>> = client.characterId.flatMapLatest { characterId ->
        characterId?.let { styleRepository.getStyleMap(it) } ?: flow {  }
    }
}