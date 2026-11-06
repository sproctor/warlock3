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

    companion object {
        /** No proxy: connect to the game server directly. */
        val NONE = ConnectionProxySettings(enabled = false, launchCommand = null, host = null, port = null)
    }
}
