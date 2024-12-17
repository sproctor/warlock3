package warlockfe.warlock3.core.prefs.models

import androidx.room.Embedded
import androidx.room.Relation

data class PopulatedHighlight(
    @Embedded val highlight: HighlightEntity,
    @Relation(parentColumn = "id", entityColumn = "highlightId")
    val styles: List<HighlightStyleEntity>
)
