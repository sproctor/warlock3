package cc.warlock.warlock3.core.highlights

import kotlinx.coroutines.flow.StateFlow

class HighlightRegistry(
    val globalHighlights: StateFlow<List<Highlight>>,
    val characterHighlights: StateFlow<Map<String, List<Highlight>>>,
    val saveGlobalHighlight: (Highlight) -> Unit,
    val saveHighlight: (String, Highlight) -> Unit,
) {
    fun getHighlights(characterId: String): List<Highlight> {
        return globalHighlights.value + (characterHighlights.value[characterId.lowercase()] ?: emptyList())
    }
}