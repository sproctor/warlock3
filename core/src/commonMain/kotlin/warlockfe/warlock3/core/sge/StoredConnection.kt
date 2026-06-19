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
)
