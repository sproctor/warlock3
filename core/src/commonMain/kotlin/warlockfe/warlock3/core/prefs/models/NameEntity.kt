package warlockfe.warlock3.core.prefs.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import warlockfe.warlock3.core.text.WarlockColor
import java.util.UUID

@Entity("Name")
data class NameEntity(
    @PrimaryKey
    val id: UUID,
    val characterId: String,
    val text: String,
    val textColor: WarlockColor,
    val backgroundColor: WarlockColor,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val fontFamily: String?,
    val fontSize: Float?,
)
