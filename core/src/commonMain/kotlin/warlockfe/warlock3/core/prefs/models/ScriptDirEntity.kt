package warlockfe.warlock3.core.prefs.models

import androidx.room3.Entity

@Entity(
    tableName = "ScriptDir",
    primaryKeys = ["characterId", "path"],
)
data class ScriptDirEntity(
    val characterId: String,
    val path: String,
)
