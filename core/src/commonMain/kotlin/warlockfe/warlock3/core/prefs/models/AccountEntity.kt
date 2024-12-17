package warlockfe.warlock3.core.prefs.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey
    val username: String,
    val password: String?,
)