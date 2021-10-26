package cc.warlock.warlock3.core.highlights

import kotlinx.coroutines.flow.Flow

class HighlightRegistry(
    val globalHighlights: Flow<List<Highlight>>,
    val characterHighlights: Flow<Map<String, List<Highlight>>>,
    val addHighlight: (String?, Highlight) -> Unit,
    val deleteHighlight: (String?, String) -> Unit,
)