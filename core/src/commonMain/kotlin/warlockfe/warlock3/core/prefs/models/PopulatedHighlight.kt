package warlockfe.warlock3.core.prefs.models

import androidx.room3.Embedded
import androidx.room3.Relation

data class PopulatedHighlight(
    @Embedded val highlight: HighlightEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["highlightId"])
    val styles: List<HighlightStyleEntity>,
)
