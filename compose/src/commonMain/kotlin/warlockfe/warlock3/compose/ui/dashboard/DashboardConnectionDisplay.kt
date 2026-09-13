package warlockfe.warlock3.compose.ui.dashboard

import androidx.compose.ui.graphics.Color
import warlockfe.warlock3.core.sge.ConnectionProtocol
import warlockfe.warlock3.core.sge.StoredConnection

// EAccess game codes the user can discover characters for via MUD Mobile. Shared by the desktop and
// mobile add-character dialogs.
internal val MUD_MOBILE_GAME_CODES = listOf("DR", "DRX", "DRF", "DRT", "GS3", "GSX", "GSF", "GST")

// Accent reserved for MUD Mobile, paired with a text cue (never color alone). Shared by the desktop
// and mobile dashboards so the marker stays identical on both.
internal val MudMobileAccent = Color(0xFF34D399)

/**
 * Secondary line for a saved connection: the game code plus context, joined by a middle dot
 * (e.g. "DR (dot) MUD Mobile" or "DR (dot) Lich proxy"). Null when there is nothing to show. Shared
 * by both clients so the desktop and mobile connection rows stay in sync.
 */
internal fun connectionSubline(connection: StoredConnection): String? {
    connection.telnetAddress?.let { address ->
        val port = if (address.tls) "${address.port} (TLS)" else address.port.toString()
        return "${address.host}:$port \u00b7 Telnet"
    }
    val context =
        when {
            connection.mudMobile -> "MUD Mobile"
            connection.proxySettings.enabled -> "Lich proxy"
            else -> null
        }
    val parts = listOfNotNull(connection.code.ifBlank { null }, context)
    return parts.joinToString(" \u00b7 ").ifBlank { null }
}

/**
 * Whether logging in needs a password we do not have. A play.net login (direct or through MUD
 * Mobile) cannot proceed without the account password, so the dashboard asks for one first; a
 * telnet MUD does its own asking, in the game window.
 */
internal fun StoredConnection.needsPasswordPrompt(): Boolean = protocol != ConnectionProtocol.TELNET && password.isNullOrBlank()

internal val StoredConnection.isTelnet: Boolean
    get() = protocol == ConnectionProtocol.TELNET

/** What the telnet connection dialog collects, on desktop and mobile alike. */
data class TelnetConnectionForm(
    val name: String,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val character: String,
    val windowTitle: String?,
)

/**
 * The form's fields checked, with the trimmed form or the first thing wrong with it. Shared so both
 * dialogs refuse the same input for the same reasons.
 */
internal fun validateTelnetForm(
    name: String,
    host: String,
    port: String,
    tls: Boolean,
    character: String,
    windowTitle: String,
): Result<TelnetConnectionForm> {
    val trimmedHost = host.trim()
    if (trimmedHost.isEmpty()) return Result.failure(IllegalArgumentException("Enter the server's host name."))
    val portNumber =
        port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
            ?: return Result.failure(IllegalArgumentException("Enter a port between 1 and 65535."))
    val trimmedCharacter = character.trim()
    if (trimmedCharacter.isEmpty()) {
        return Result.failure(IllegalArgumentException("Enter your character's name; settings are saved per character."))
    }
    return Result.success(
        TelnetConnectionForm(
            name = name.trim().ifEmpty { "$trimmedCharacter @ $trimmedHost" },
            host = trimmedHost,
            port = portNumber,
            tls = tls,
            character = trimmedCharacter,
            windowTitle = windowTitle.trim().ifBlank { null },
        ),
    )
}
