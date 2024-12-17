package warlockfe.warlock3.core.prefs.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "alteration")
data class AlterationEntity(
    @PrimaryKey
    val id: UUID,
    val characterId: String,
    val pattern: String,
    val sourceStream: String?,
    val destinationStream: String?,
    val result: String?,
    val ignoreCase: Boolean,
    val keepOriginal: Boolean,
)