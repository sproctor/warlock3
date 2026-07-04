package warlockfe.warlock3.core.prefs.models

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.uuid.Uuid

@Entity(
    tableName = "Highlight",
    indices = [
        Index(value = ["characterId", "pattern"], unique = true),
    ],
)
data class HighlightEntity(
    @PrimaryKey
    val id: Uuid,
    val characterId: String,
    val pattern: String,
    val isRegex: Boolean,
    val matchPartialWord: Boolean,
    val ignoreCase: Boolean,
    val sound: String?,
)
