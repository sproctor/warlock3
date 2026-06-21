package warlockfe.warlock3.compose.ui.dashboard

import warlockfe.warlock3.core.sge.StoredConnection

// EAccess game codes the user can discover characters for via MUD Mobile. Shared by the desktop and
// mobile add-character dialogs.
internal val MUD_MOBILE_GAME_CODES = listOf("DR", "DRX", "DRF", "DRT", "GS3", "GSX", "GSF", "GST")

/**
 * Secondary line for a saved connection: the game code plus context, joined by a middle dot
 * (e.g. "DR (dot) MUD Mobile" or "DR (dot) Lich proxy"). Null when there is nothing to show. Shared
 * by both clients so the desktop and mobile connection rows stay in sync.
 */
internal fun connectionSubline(connection: StoredConnection): String? {
    val context =
        when {
            connection.mudMobile -> "MUD Mobile"
            connection.proxySettings.enabled -> "Lich proxy"
            else -> null
        }
    val parts = listOfNotNull(connection.code.ifBlank { null }, context)
    return parts.joinToString(" \u00b7 ").ifBlank { null }
}
