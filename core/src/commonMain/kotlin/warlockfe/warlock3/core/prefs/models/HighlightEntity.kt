package warlockfe.warlock3.core.prefs.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

@Entity(
    tableName = "Highlight",
    indices = [
        Index(value = ["characterId", "pattern"], unique = true)
    ]
)
data class HighlightEntity(
    @PrimaryKey
    val id: UUID,
    val characterId: String,
    val pattern: String,
    val isRegex: Boolean,
    val matchPartialWord: Boolean,
    val ignoreCase: Boolean,
    val sound: String?,
)
