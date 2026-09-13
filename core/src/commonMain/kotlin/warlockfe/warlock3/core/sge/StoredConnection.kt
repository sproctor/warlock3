package warlockfe.warlock3.core.sge

data class StoredConnection(
    val id: String,
    val name: String,
    val username: String,
    val password: String?,
    val character: String,
    val code: String,
    // When set, the game window title shows "Warlock - <windowTitle>" instead of the character name.
    val windowTitle: String? = null,
    val proxySettings: ConnectionProxySettings,
    // When true, this connection is played through MUD Mobile's hosted Lich rather than directly
    // to play.net. [characterCode] is the EAccess character code (known for MUD Mobile profiles).
    val mudMobile: Boolean = false,
    val characterCode: String? = null,
    val protocol: ConnectionProtocol = ConnectionProtocol.SGE,
    // Telnet connections only: where to dial. Null on SGE connections, whose game host comes from
    // the login server at connect time.
    val host: String? = null,
    val port: Int? = null,
    val tls: Boolean = false,
) {
    /** The telnet address of this connection, or null when it is not a telnet connection. */
    val telnetAddress: TelnetAddress?
        get() =
            if (protocol == ConnectionProtocol.TELNET && host != null && port != null) {
                TelnetAddress(host = host, port = port, tls = tls)
            } else {
                null
            }
}

data class TelnetAddress(
    val host: String,
    val port: Int,
    val tls: Boolean,
)
