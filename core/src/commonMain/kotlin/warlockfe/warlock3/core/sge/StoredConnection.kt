package warlockfe.warlock3.core.sge

data class StoredConnection(
    val id: String,
    val name: String,
    val username: String,
    val password: String?,
    val character: String,
    val code: String,
    val proxySettings: ConnectionProxySettings,
)
