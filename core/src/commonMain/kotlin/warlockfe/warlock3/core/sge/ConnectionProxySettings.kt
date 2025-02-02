package warlockfe.warlock3.core.sge

data class ConnectionProxySettings(
    val enabled: Boolean,
    val launchCommand: String?,
    val host: String?,
    val port: String?,
)