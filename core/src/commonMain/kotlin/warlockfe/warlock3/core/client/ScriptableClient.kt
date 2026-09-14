package warlockfe.warlock3.core.client

import kotlinx.coroutines.flow.StateFlow

/**
 * The parts of a client a script may drive. A Simutronics game states these in its protocol (the
 * roundtime, what spell is being cast) and a telnet MUD does not, so on a telnet connection a
 * script fills them in from the text or from GMCP - as a rule, the script the MUD itself sent
 * (see [mudScript]).
 */
interface ScriptableClient {
    /**
     * The script the MUD has offered to install, when it has: a `Client.GUI` GMCP message naming a
     * version and a URL, the way Mudlet is offered its interface packages. Null until one arrives,
     * and never set on a connection whose user declined scripts.
     */
    val mudScript: StateFlow<MudScriptOffer?>

    /** Sets when the roundtime ends, in seconds since the epoch, or clears it. */
    fun setRoundTime(endSeconds: Long?)

    /** Sets when the cast time ends, in seconds since the epoch, or clears it. */
    fun setCastTime(endSeconds: Long?)

    /**
     * Sends a GMCP message to the server: the package and message name (`Core.Supports.Add`) and
     * the JSON. Dropped when the server never negotiated GMCP.
     */
    suspend fun sendGmcp(
        name: String,
        data: String,
    )
}

/**
 * A script the MUD offered over GMCP: its version, and either where to download it ([url]) or the
 * Lua itself ([script]), one of which is always present. A new version replaces the last.
 */
data class MudScriptOffer(
    val version: String,
    val url: String? = null,
    val script: String? = null,
)
