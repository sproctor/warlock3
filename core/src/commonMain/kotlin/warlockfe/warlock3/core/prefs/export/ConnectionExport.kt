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
    // Telnet connections; absent (the defaults) in exports from before telnet support, which are
    // all SGE connections.
    val protocol: String? = null,
    val host: String? = null,
    val port: Int? = null,
    val tls: Boolean = false,
)
