package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionExport(
    val id: String,
    val name: String,
    val username: String,
    val gameCode: String,
    val character: String,
    val settings: Map<String, String>,
)
