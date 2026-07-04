package warlockfe.warlock3.core.prefs.models

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey
    val username: String,
    val password: String?,
)
