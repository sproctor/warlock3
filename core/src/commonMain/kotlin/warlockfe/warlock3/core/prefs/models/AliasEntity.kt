package warlockfe.warlock3.core.prefs.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

@Entity(tableName = "alias")
data class AliasEntity(
    @PrimaryKey
    val id: Uuid,
    val characterId: String,
    val pattern: String,
    val replacement: String,
)