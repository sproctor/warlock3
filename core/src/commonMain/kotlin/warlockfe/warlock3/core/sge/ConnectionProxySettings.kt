package warlockfe.warlock3.core.sge

data class ConnectionProxySettings(
    val enabled: Boolean,
    val launchCommand: String?,
    val host: String?,
    val port: String?,
) {
    /** True when any proxy connection detail (launch command, host, or port) has been provided. */
    val hasDetails: Boolean
        get() = !launchCommand.isNullOrBlank() || !host.isNullOrBlank() || !port.isNullOrBlank()
}
