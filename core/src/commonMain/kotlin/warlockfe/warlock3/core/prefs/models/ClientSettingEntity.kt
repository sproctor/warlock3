package warlockfe.warlock3.core.prefs.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clientsetting")
data class ClientSettingEntity(
    @PrimaryKey
    val key: String,
    val value: String?,
)
