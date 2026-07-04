package warlockfe.warlock3.core.prefs.models

import androidx.room3.ColumnInfo
import androidx.room3.Entity

@Entity(
    tableName = "connection_setting",
    primaryKeys = ["connection_id", "key"],
)
data class ConnectionSettingEntity(
    @ColumnInfo(name = "connection_id")
    val connectionId: String,
    val key: String,
    val value: String,
)
