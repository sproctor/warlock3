package warlockfe.warlock3.core.prefs.models

import androidx.room.Entity
import androidx.room.ForeignKey
import warlockfe.warlock3.core.text.WarlockColor
import kotlin.uuid.Uuid

@Entity(
    tableName = "highlightstyle",
    primaryKeys = ["highlightId", "groupNumber"],
    foreignKeys = [
        ForeignKey(
            entity = HighlightEntity::class,
            parentColumns = ["id"],
            childColumns = ["highlightId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class HighlightStyleEntity(
    val highlightId: Uuid,
    val groupNumber: Int,
    val textColor: WarlockColor,
    val backgroundColor: WarlockColor,
    val entireLine: Boolean,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val fontFamily: String?,
    val fontSize: Float?,
)
