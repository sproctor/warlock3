package warlockfe.warlock3.core.prefs.models

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "character")
data class CharacterEntity(
    @PrimaryKey
    val id: String,
    val gameCode: String,
    val name: String,
)
