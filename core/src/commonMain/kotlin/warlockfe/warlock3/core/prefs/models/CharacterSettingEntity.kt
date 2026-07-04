package warlockfe.warlock3.core.prefs.models

import androidx.room3.Entity

@Entity(
    tableName = "charactersetting",
    primaryKeys = ["characterId", "key"],
)
data class CharacterSettingEntity(
    val characterId: String,
    val key: String,
    val value: String,
)
