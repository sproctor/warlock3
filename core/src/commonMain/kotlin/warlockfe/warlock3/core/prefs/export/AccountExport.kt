package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable

@Serializable
data class AccountExport(
    val username: String,
    val password: String?,
)
