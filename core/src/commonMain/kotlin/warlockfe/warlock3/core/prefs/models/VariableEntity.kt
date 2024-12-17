package warlockfe.warlock3.core.prefs.models

import androidx.room.Entity

@Entity(
    tableName = "Variable",
    primaryKeys = ["characterId", "name"],
)
data class VariableEntity(
    val characterId: String,
    val name: String,
    val value: String,
)
