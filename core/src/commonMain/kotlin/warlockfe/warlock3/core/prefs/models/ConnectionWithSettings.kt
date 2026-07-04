package warlockfe.warlock3.core.prefs.models

import androidx.room3.Embedded
import androidx.room3.Relation

data class ConnectionWithSettings(
    @Embedded
    val connection: ConnectionEntity,
    @Relation(parentColumns = ["username"], entityColumns = ["username"])
    val account: AccountEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["connection_id"])
    val settings: List<ConnectionSettingEntity>,
)
