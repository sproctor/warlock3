package warlockfe.warlock3.core.prefs.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "alias")
data class AliasEntity(
    @PrimaryKey
    val id: UUID,
    val characterId: String,
    val pattern: String,
    val replacement: String,
)