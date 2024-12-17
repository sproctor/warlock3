package warlockfe.warlock3.core.prefs.models

import androidx.room.Entity

@Entity(
    tableName = "macro",
    primaryKeys = ["characterId", "key"],
)
data class MacroEntity(
    val characterId: String,
    val key: String,
    val value: String,
)
