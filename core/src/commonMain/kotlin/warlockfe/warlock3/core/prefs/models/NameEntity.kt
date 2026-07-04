package warlockfe.warlock3.core.prefs.models

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import warlockfe.warlock3.core.text.WarlockColor
import kotlin.uuid.Uuid

@Entity(
    tableName = "Name",
    indices = [
        Index(value = ["characterId", "text"], unique = true),
    ],
)
data class NameEntity(
    @PrimaryKey
    val id: Uuid,
    val characterId: String,
    val text: String,
    val textColor: WarlockColor,
    val backgroundColor: WarlockColor,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val fontFamily: String?,
    val fontSize: Float?,
    val fontWeight: Int?,
    val sound: String?,
)
