package warlockfe.warlock3.core.prefs.models

import androidx.room.Embedded
import androidx.room.Relation

data class ConnectionWithSettings(
    @Embedded
    val connection: ConnectionEntity,
    @Relation(parentColumn = "username", entityColumn = "username")
    val account: AccountEntity,
    @Relation(parentColumn = "id", entityColumn = "connection_id")
    val settings: List<ConnectionSettingEntity>,
)