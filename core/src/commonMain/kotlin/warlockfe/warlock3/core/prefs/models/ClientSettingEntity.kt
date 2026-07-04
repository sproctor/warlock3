package warlockfe.warlock3.core.prefs.models

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "clientsetting")
data class ClientSettingEntity(
    @PrimaryKey
    val key: String,
    val value: String?,
)
