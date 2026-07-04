package warlockfe.warlock3.core.prefs.models

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "connection")
data class ConnectionEntity(
    @PrimaryKey
    val id: String,
    val username: String,
    @ColumnInfo(name = "game_code")
    val gameCode: String,
    val character: String,
    val name: String,
)
