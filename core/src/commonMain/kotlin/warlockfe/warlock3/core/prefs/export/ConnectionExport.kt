package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionExport(
    val name: String,
    val username: String,
    val settings: Map<String, String>,
)
