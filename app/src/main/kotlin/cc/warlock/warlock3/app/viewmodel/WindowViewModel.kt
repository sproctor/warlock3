package cc.warlock.warlock3.app.viewmodel

import cc.warlock.warlock3.app.model.ViewHighlight
import cc.warlock.warlock3.app.util.toSpanStyle
import cc.warlock.warlock3.core.prefs.HighlightRepository
import cc.warlock.warlock3.core.prefs.models.PresetRepository
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.window.Window
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class WindowViewModel(
    val name: String,
    client: StormfrontClient,
    val window: Flow<Window?>,
    val highlightRepository: HighlightRepository,
    private val presetRepository: PresetRepository,
) {

    val components = client.components

    val lines = client.getStream(name).lines

    @OptIn(ExperimentalCoroutinesApi::class)
    val highlights = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            highlightRepository.observeForCharacter(characterId)
                .map { highlights ->
                    highlights.map { highlight ->
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
                            styles = highlight.styles.mapValues { it.value.toSpanStyle() }
                        )
                    }
                }
        } else {
            flow {
                emit(emptyList())
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val presets: Flow<Map<String, StyleDefinition>> = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            presetRepository.observePresetsForCharacter(characterId)
        } else {
            flow { emit(emptyMap()) }
        }
    }
}