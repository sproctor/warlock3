package cc.warlock.warlock3.core.highlights

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

class HighlightRegistry(
    val globalHighlights: Flow<List<Highlight>>,
    val characterHighlights: Flow<Map<String, List<Highlight>>>,
    val saveGlobalHighlight: (Highlight) -> Unit,
    val saveHighlight: (String, Highlight) -> Unit,
) {
    fun getHighlights(characterId: String): Flow<List<Highlight>> =
        combine(globalHighlights, characterHighlights) { globalHighlights, characterHighlights ->
            globalHighlights + (characterHighlights[characterId.lowercase()] ?: emptyList())
        }
}